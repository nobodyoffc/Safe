#!/usr/bin/env python3
import re
import sys

def process_java_file(filepath):
    """Process a single Java file to replace Toast with ToastUtils"""

    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    original = content
    changes = 0

    # Add ToastUtils import if not present and Toast.makeText is found
    if 'Toast.makeText' in content and 'import com.fc.safe.utils.ToastUtils;' not in content:
        # Find last import from com.fc.safe
        safe_imports = list(re.finditer(r'^import com\.fc\.safe\..*?;$', content, re.MULTILINE))
        if safe_imports:
            last_import = safe_imports[-1]
            content = content[:last_import.end()] + '\nimport com.fc.safe.utils.ToastUtils;' + content[last_import.end():]
            changes += 1
        else:
            # Find any import and add after
            imports = list(re.finditer(r'^import .*?;$', content, re.MULTILINE))
            if imports:
                last_import = imports[-1]
                content = content[:last_import.end()] + '\nimport com.fc.safe.utils.ToastUtils;' + content[last_import.end():]
                changes += 1

    # Replace Toast.makeText patterns
    # Pattern 1: Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
    pattern1 = r'Toast\.makeText\(([^,)]+),\s*([^,)]+),\s*Toast\.LENGTH_SHORT\)\.show\(\)'
    matches1 = re.findall(pattern1, content)
    content = re.sub(pattern1, r'ToastUtils.makeText(\1, \2)', content)
    changes += len(matches1)

    # Pattern 2: Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
    pattern2 = r'Toast\.makeText\(([^,)]+),\s*([^,)]+),\s*Toast\.LENGTH_LONG\)\.show\(\)'
    matches2 = re.findall(pattern2, content)
    content = re.sub(pattern2, r'ToastUtils.makeText(\1, \2, Toast.LENGTH_LONG, "INFO")', content)
    changes += len(matches2)

    # Remove Toast import if no longer needed
    if 'import android.widget.Toast;' in content and 'Toast.' not in content:
        content = re.sub(r'import android\.widget\.Toast;\n', '', content)
        changes += 1

    if content != original:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        return changes
    return 0

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("Usage: python batch_replace.py <file>")
        sys.exit(1)

    filepath = sys.argv[1]
    changes = process_java_file(filepath)
    print(f"{filepath}: {changes} changes")
