import importlib.util
import json
import sqlite3
import sys
import tempfile
import types
import unittest
from pathlib import Path


def load_server_module():
    """Load the server without requiring the optional MCP transport in test runners."""
    if "mcp.server.fastmcp" not in sys.modules:
        class DummyFastMCP:
            def __init__(self, *args, **kwargs):
                pass

            def tool(self):
                return lambda function: function

            def run(self):
                pass

        mcp_package = types.ModuleType("mcp")
        server_package = types.ModuleType("mcp.server")
        fastmcp_module = types.ModuleType("mcp.server.fastmcp")
        fastmcp_module.FastMCP = DummyFastMCP
        sys.modules.setdefault("mcp", mcp_package)
        sys.modules.setdefault("mcp.server", server_package)
        sys.modules.setdefault("mcp.server.fastmcp", fastmcp_module)

    path = Path(__file__).with_name("backtester_mcp.py")
    spec = importlib.util.spec_from_file_location("backtester_mcp_under_test", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


server = load_server_module()


class McpSqlSecurityTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.db_path = Path(self.temporary.name) / "security.db"
        conn = sqlite3.connect(self.db_path)
        try:
            conn.executescript("""
                CREATE TABLE APP_SETTINGS(key TEXT PRIMARY KEY, value TEXT);
                INSERT INTO APP_SETTINGS VALUES('openrouter.api.key', 'FAKE_SECRET');
                CREATE TABLE SENSITIVITY_DETAIL(pass_number INTEGER, verdict TEXT);
                INSERT INTO SENSITIVITY_DETAIL VALUES(7, 'ROBUST');
                CREATE TABLE HISTORY_RUNS(id INTEGER);
                CREATE TABLE OPTIMIZATION_STATE(id INTEGER);
                CREATE TABLE EA_SAVED_CONFIGS(id INTEGER);
                CREATE TABLE OTHER_PRIVATE(value TEXT);
                INSERT INTO OTHER_PRIVATE VALUES('PRIVATE');
                CREATE VIEW harmless_alias AS SELECT value FROM APP_SETTINGS;
            """)
            conn.commit()
        finally:
            conn.close()
        self.original_path = server.DB_PATH
        server.DB_PATH = self.db_path

    def tearDown(self):
        server.DB_PATH = self.original_path
        self.temporary.cleanup()

    def test_documented_table_remains_queryable(self):
        result = json.loads(server.query_database(
            "SELECT pass_number, verdict FROM SENSITIVITY_DETAIL"))
        self.assertEqual([{"pass_number": 7, "verdict": "ROBUST"}], result)

    def test_secret_table_is_blocked_directly_and_through_view(self):
        direct = server.query_database("SELECT value FROM APP_SETTINGS")
        indirect = server.query_database("SELECT * FROM harmless_alias")
        self.assertIn("gesperrt", direct)
        self.assertDenied(indirect)
        self.assertNotIn("FAKE_SECRET", direct + indirect)

    def test_positive_table_allowlist_blocks_undocumented_tables(self):
        result = server.query_database("SELECT * FROM OTHER_PRIVATE")
        self.assertDenied(result)
        self.assertNotIn("'PRIVATE'", result)

    def test_pragma_attach_and_dangerous_functions_are_denied_by_authorizer(self):
        with sqlite3.connect(":memory:") as conn:
            server._guard_query_connection(conn)
            with self.assertRaises(sqlite3.DatabaseError):
                conn.execute("PRAGMA database_list").fetchall()
            with self.assertRaises(sqlite3.DatabaseError):
                conn.execute("ATTACH DATABASE ':memory:' AS other")
            with self.assertRaises(sqlite3.DatabaseError):
                conn.execute("SELECT load_extension('missing')").fetchall()

        table_pragma = server.query_database(
            "SELECT * FROM pragma_table_info('APP_'||'SETTINGS')")
        self.assertDenied(table_pragma)

    def test_row_and_response_size_are_bounded(self):
        too_many = server.query_database(
            "SELECT * FROM (WITH RECURSIVE n(x) AS (VALUES(1) UNION ALL "
            f"SELECT x+1 FROM n WHERE x<{server.QUERY_MAX_ROWS + 1}) SELECT x FROM n)")
        self.assertIn(f"mehr als {server.QUERY_MAX_ROWS} Zeilen", too_many)

        chunk = server.QUERY_MAX_RESPONSE_BYTES // 3
        too_large = server.query_database(
            "SELECT hex(zeroblob(%d)) AS payload FROM "
            "(SELECT 1 UNION ALL SELECT 2)" % chunk)
        self.assertTrue("größer" in too_large or "too big" in too_large.lower())

    def test_progress_handler_interrupts_expensive_nested_cte(self):
        result = server.query_database(
            "SELECT sum(x) FROM (WITH RECURSIVE n(x) AS "
            "(VALUES(1) UNION ALL SELECT x+1 FROM n) SELECT x FROM n)")
        self.assertIn("Rechenlimit abgebrochen", result)

    def test_schema_hides_app_settings(self):
        schema = server.get_database_schema()
        self.assertNotIn("APP_SETTINGS", schema.upper())
        self.assertIn("SENSITIVITY_DETAIL", schema)

    def assertDenied(self, result):
        lowered = result.lower()
        self.assertTrue("nicht freigegebene" in lowered
                        or "not authorized" in lowered or "prohibited" in lowered,
                        result)


if __name__ == "__main__":
    unittest.main()
