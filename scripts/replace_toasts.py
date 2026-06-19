#!/usr/bin/env python3
"""
Script to replace Toast.makeText() calls with ToastUtils in the Safe Android project.
This script intelligently categorizes Toast messages as errors, warnings, or info
based on context and message content.
"""

import re
import os
import sys
from pathlib import Path

class ToastReplacer:
    def __init__(self, project_root):
        self.project_root = Path(project_root)
        self.safe_dir = self.project_root / "app/src/main/java/com/fc/safe"
        self.stats = {
            'files_processed': 0,
            'toasts_replaced': 0,
            'errors': []
        }

    def should_skip_file(self, filepath):
        """Check if file should be skipped"""
        filename = os.path.basename(filepath)
        return filename in ['ToastActivity.java', 'ToastUtils.java']

    def categorize_toast(self, context, message):
        """Categorize toast message as error, warning, or info"""
        message_lower = message.lower()

        # Error indicators
        error_keywords = ['error', 'fail', 'incorrect', 'invalid', 'cannot', 'unable', 'exception']
        if any(keyword in message_lower for keyword in error_keywords):
            return 'showError'

        # Warning indicators
        warning_keywords = ['warn', 'empty', 'no_', 'nothing', 'select', 'required', 'missing', 'not_found']
        if any(keyword in message_lower for keyword in warning_keywords):
            return 'showWarning'

        # Default to info for success, saved, copied, etc.
        return 'showInfo'

    def process_message(self, context, message):
        """Process the message parameter to ensure proper getString() usage"""
        message = message.strip()

        # If message is already a getString() call, return as is
        if message.startswith('getString('):
            return message

        # If message is R.string.xxx, wrap it with getString()
        if message.startswith('R.string.'):
            # Check if there are format arguments
            if ', ' in message:
                return message  # Keep as is if it has arguments
            return f'{context}.getString({message})'

        # If message contains getString() somewhere, extract and use it
        get_string_match = re.search(r'getString\s*\(\s*(R\.string\.[a-zA-Z_0-9]+)(?:,\s*(.+))?\s*\)', message)
        if get_string_match:
            string_res = get_string_match.group(1)
            args = get_string_match.group(2)
            if args:
                return f'{context}.getString({string_res}, {args})'
            return f'{context}.getString({string_res})'

        # If it's a plain string literal, return as is
        return message

    def replace_toasts_in_file(self, filepath):
        """Replace all Toast.makeText() calls in a file with ToastUtils"""
        try:
            with open(filepath, 'r', encoding='utf-8') as f:
                content = f.read()

            original_content = content
            toast_count = 0

            # Skip if no Toast.makeText
            if 'Toast.makeText' not in content:
                return 0

            # Add ToastUtils import if not present
            if 'import com.fc.safe.utils.ToastUtils;' not in content:
                # Find imports section and add ToastUtils import
                import_pattern = r'(import\s+[^;]+;)'
                imports = list(re.finditer(import_pattern, content))
                if imports:
                    last_import_pos = imports[-1].end()
                    content = (content[:last_import_pos] +
                              '\nimport com.fc.safe.utils.ToastUtils;' +
                              content[last_import_pos:])

            # Pattern to match Toast.makeText(...).show()
            # Captures: context, message, length
            toast_pattern = r'Toast\.makeText\s*\(\s*([^,]+?)\s*,\s*(.+?)\s*,\s*Toast\.LENGTH_(SHORT|LONG)\s*\)\.show\(\)'

            def replace_match(match):
                nonlocal toast_count
                toast_count += 1

                context = match.group(1).strip()
                raw_message = match.group(2).strip()
                length = match.group(3)  # SHORT or LONG (not currently used)

                # Process the message
                message = self.process_message(context, raw_message)

                # Determine the appropriate ToastUtils method
                method = self.categorize_toast(context, raw_message)

                return f'ToastUtils.{method}({context}, {message})'

            # Perform replacements
            content = re.sub(toast_pattern, replace_match, content)

            # Remove Toast import if no longer needed
            if toast_count > 0:
                # Check if Toast is still referenced (excluding ToastUtils)
                remaining_toast_refs = re.findall(r'\bToast\.', content)
                toastutils_refs = re.findall(r'\bToastUtils\.', content)

                if len(remaining_toast_refs) == 0 or len(remaining_toast_refs) == len(toastutils_refs):
                    content = re.sub(r'import\s+android\.widget\.Toast;\s*\n', '', content)

            # Write back if changes were made
            if content != original_content:
                # Create backup
                backup_path = str(filepath) + '.bak'
                with open(backup_path, 'w', encoding='utf-8') as f:
                    f.write(original_content)

                with open(filepath, 'w', encoding='utf-8') as f:
                    f.write(content)

                return toast_count

            return 0

        except Exception as e:
            self.stats['errors'].append(f"{filepath}: {str(e)}")
            return 0

    def process_all_files(self):
        """Process all Java files in the project"""
        java_files = list(self.safe_dir.rglob('*.java'))

        print("=" * 80)
        print("Toast.makeText to ToastUtils Replacement Script")
        print("=" * 80)
        print(f"\nFound {len(java_files)} Java files to scan\n")

        for filepath in java_files:
            if self.should_skip_file(str(filepath)):
                continue

            replaced = self.replace_toasts_in_file(filepath)
            if replaced > 0:
                relative_path = filepath.relative_to(self.project_root)
                print(f"✓ {relative_path}: {replaced} Toast calls replaced")
                self.stats['files_processed'] += 1
                self.stats['toasts_replaced'] += replaced

        self.print_summary()

    def print_summary(self):
        """Print summary statistics"""
        print("\n" + "=" * 80)
        print("SUMMARY")
        print("=" * 80)
        print(f"Files processed: {self.stats['files_processed']}")
        print(f"Total Toast.makeText calls replaced: {self.stats['toasts_replaced']}")

        if self.stats['errors']:
            print(f"\nErrors encountered: {len(self.stats['errors'])}")
            for error in self.stats['errors']:
                print(f"  - {error}")

        print("\nBackup files created with .bak extension")
        print("Please review changes before committing!")
        print("=" * 80)

def main():
    if len(sys.argv) > 1:
        project_root = sys.argv[1]
    else:
        project_root = "/Users/liuchangyong/AndroidStudioProjects/Safe"

    if not os.path.exists(project_root):
        print(f"Error: Project root not found: {project_root}")
        sys.exit(1)

    replacer = ToastReplacer(project_root)
    replacer.process_all_files()

if __name__ == '__main__':
    main()
