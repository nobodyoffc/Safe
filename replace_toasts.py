#!/usr/bin/env python3
"""
Script to replace Toast.makeText() calls with ToastUtils in Android Java files.
"""

import re
import os
from pathlib import Path

# Files to exclude
EXCLUDE_FILES = {'ToastActivity.java', 'ToastUtils.java'}

def process_file(file_path):
    """Process a single Java file to replace Toast calls with ToastUtils."""
    changes = []

    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()

    original_content = content

    # Check if file should be excluded
    if os.path.basename(file_path) in EXCLUDE_FILES:
        return changes

    # Check if ToastUtils import already exists
    has_toast_utils_import = 'import com.fc.safe.utils.ToastUtils;' in content

    # Check if Toast import exists
    has_toast_import = 'import android.widget.Toast;' in content

    # Count Toast.makeText occurrences
    toast_count = len(re.findall(r'Toast\.makeText', content))

    if toast_count == 0:
        return changes

    # Add ToastUtils import if not present
    if not has_toast_utils_import and toast_count > 0:
        # Find the right place to add import (after other imports)
        import_match = re.search(r'(import com\.fc\.safe\..*?;)', content)
        if import_match:
            last_safe_import = import_match.group(1)
            content = content.replace(
                last_safe_import,
                last_safe_import + '\nimport com.fc.safe.utils.ToastUtils;'
            )
            changes.append("Added ToastUtils import")

    # Replace Toast.makeText patterns

    # Pattern 1: Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    pattern1 = r'Toast\.makeText\(([^,]+),\s*([^,]+),\s*Toast\.LENGTH_SHORT\)\.show\(\)'
    if re.search(pattern1, content):
        content = re.sub(pattern1, r'ToastUtils.makeText(\1, \2)', content)
        changes.append(f"Replaced {len(re.findall(pattern1, original_content))} SHORT toast calls")

    # Pattern 2: Toast.makeText(context, text, Toast.LENGTH_LONG).show() (errors/warnings)
    pattern2 = r'Toast\.makeText\(([^,]+),\s*([^,]+),\s*Toast\.LENGTH_LONG\)\.show\(\)'
    matches = re.findall(pattern2, content)
    for match in matches:
        # Try to determine if it's an error or warning based on context
        full_pattern = f'Toast.makeText({match[0]}, {match[1]}, Toast.LENGTH_LONG).show()'
        # Default to showError for LONG toasts
        replacement = f'ToastUtils.showError({match[0]}, {match[1]})'
        content = content.replace(full_pattern, replacement)
    if matches:
        changes.append(f"Replaced {len(matches)} LONG toast calls")

    # Remove Toast import if no longer needed
    if has_toast_import and 'Toast.' not in content:
        content = re.sub(r'import android\.widget\.Toast;\n?', '', content)
        changes.append("Removed Toast import")

    # Write back if changes were made
    if content != original_content:
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(content)
        return changes

    return []

def main():
    """Main function to process all Java files."""
    base_dir = Path('/Users/liuchangyong/AndroidStudioProjects/Safe/app/src/main/java/com/fc/safe')

    java_files = list(base_dir.rglob('*.java'))

    total_files = 0
    total_changes = 0
    processed_files = []

    for java_file in java_files:
        if java_file.name in EXCLUDE_FILES:
            continue

        changes = process_file(str(java_file))
        if changes:
            total_files += 1
            total_changes += len(changes)
            processed_files.append({
                'file': str(java_file),
                'changes': changes
            })

    # Print summary
    print(f"\n=== Summary ===")
    print(f"Total files processed: {total_files}")
    print(f"Total changes made: {total_changes}")
    print(f"\nProcessed files:")
    for item in processed_files:
        print(f"\n{item['file']}:")
        for change in item['changes']:
            print(f"  - {change}")

if __name__ == '__main__':
    main()
