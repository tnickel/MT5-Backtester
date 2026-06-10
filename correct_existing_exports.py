import os
import re
import sqlite3
import json

db_path = r"C:\Users\tnickel\.mt5_backtester\history.db"
folders = [
    r"D:\AntiGravitySoftware\GitWorkspace\Backtester\export_last_test",
    r"D:\AntiGravitySoftware\GitWorkspace\Backtester\export_last_test1"
]

def is_magic_number_parameter(name):
    if not name:
        return False
    lower = name.lower()
    return lower in ("magic", "inpmagicnumber", "magicnumber")

def process_set_file(file_path, pass_num):
    # Read UTF-16 LE (with fallback to UTF-8)
    try:
        with open(file_path, "rb") as f:
            bytes_content = f.read()
        # Check for BOM
        if bytes_content.startswith(b"\xff\xfe"):
            content = bytes_content.decode("utf-16-le")
        elif bytes_content.startswith(b"\xfe\xff"):
            content = bytes_content.decode("utf-16-be")
        else:
            # check null bytes
            if b"\x00" in bytes_content:
                content = bytes_content.decode("utf-16-le")
            else:
                content = bytes_content.decode("utf-8")
    except Exception as e:
        print(f"    Failed to read file {file_path}: {e}")
        return False

    lines = content.splitlines()
    new_lines = []
    modified = False
    
    for line in lines:
        stripped = line.strip()
        if not stripped or stripped.startswith(";"):
            new_lines.append(line)
            continue
            
        eq_idx = line.find("=")
        if eq_idx <= 0:
            new_lines.append(line)
            continue
            
        name = line[:eq_idx].strip()
        value_part = line[eq_idx+1:]
        
        if is_magic_number_parameter(name):
            # Parse optimization fields if present
            if "||" in value_part:
                parts = value_part.split("||")
                old_val = parts[0].strip()
                parts[0] = str(pass_num)
                new_value = "||".join(parts)
            else:
                new_value = str(pass_num)
            
            new_line = f"{name}={new_value}"
            if line != new_line:
                print(f"    Updating parameter {name}: {line.strip()} -> {new_line}")
                line = new_line
                modified = True
        
        new_lines.append(line)
        
    # Write back as UTF-16 LE with BOM
    if modified:
        try:
            out_content = "\ufeff" + "\r\n".join(new_lines) + "\r\n"
            with open(file_path, "w", encoding="utf-16-le") as f:
                f.write(out_content)
        except Exception as e:
            print(f"    Failed to write file {file_path}: {e}")
            return False
            
    return True

def main():
    if not os.path.exists(db_path):
        print(f"Database not found at {db_path}")
        return

    print("Connecting to database...")
    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()

    for folder in folders:
        print(f"\nProcessing folder: {folder}")
        if not os.path.exists(folder):
            print("  Folder does not exist. Skipping.")
            continue
            
        for filename in sorted(os.listdir(folder)):
            if not filename.endswith(".set"):
                continue
                
            name_part = filename[:-4] # strip .set
            match = re.match(r"^(.*?)(?:_\d+proz)?_Pass(\d+)$", name_part)
            if not match:
                print(f"  Skipping {filename} (name does not match pattern)")
                continue
                
            base_prefix = match.group(1)
            pass_num = int(match.group(2))
            
            left_parts = base_prefix.rsplit("_", 2)
            if len(left_parts) < 3:
                print(f"  Skipping {filename} (prefix too short: {base_prefix})")
                continue
                
            ea_name = left_parts[0]
            symbol = left_parts[1]
            period = left_parts[2]
            
            print(f"  Found file: {filename}")
            print(f"    Parsed: EA={ea_name}, Symbol={symbol}, Period={period}, Pass={pass_num}")
            
            # Query the database for the 2y drawdown
            cursor.execute("""
                SELECT result_2y_json FROM STRATEGY_AUTOMATIC_REVIEWS 
                WHERE (expert_name = ? OR expert_name LIKE ?) AND symbol = ? AND period = ? AND pass_number = ?
            """, (ea_name, f"%{ea_name}", symbol, period, pass_num))
            
            row = cursor.fetchone()
            dd_val = 0.0
            if row and row[0]:
                try:
                    data = json.loads(row[0])
                    dd_val = data.get("maxDrawdown", 0.0)
                except Exception as e:
                    print(f"    Error parsing result_2y_json: {e}")
            else:
                print("    Warning: No database entry found in STRATEGY_AUTOMATIC_REVIEWS for 2-year review.")
                
            dd_pct = int(round(dd_val))
            print(f"    2-year Drawdown: {dd_val}% -> rounded: {dd_pct}%")
            
            # Update magic number in place
            file_path = os.path.join(folder, filename)
            success = process_set_file(file_path, pass_num)
            
            if success:
                # Rename the file
                new_filename = f"{ea_name}_{symbol}_{period}_{dd_pct}proz_Pass{pass_num}.set"
                new_path = os.path.join(folder, new_filename)
                if file_path != new_path:
                    try:
                        if os.path.exists(new_path):
                            os.remove(new_path)
                        os.rename(file_path, new_path)
                        print(f"    Renamed to: {new_filename}")
                    except Exception as e:
                        print(f"    Failed to rename file: {e}")
                else:
                    print("    Filename is already correct.")

    conn.close()
    print("\nProcessing completed successfully.")

if __name__ == "__main__":
    main()
