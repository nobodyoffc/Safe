#!/bin/bash

# Script to replace remaining Toast.makeText calls with ToastUtils
# This script processes all Java files in the Safe project

PROJECT_ROOT="/Users/liuchangyong/AndroidStudioProjects/Safe"
SAFE_DIR="$PROJECT_ROOT/app/src/main/java/com/fc/safe"

# Color codes for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Counter for statistics
TOTAL_FILES=0
TOTAL_REPLACEMENTS=0

echo "=================================================="
echo "Toast.makeText to ToastUtils Replacement Script"
echo "=================================================="
echo ""

# Function to process a single file
process_file() {
    local file="$1"
    local filename=$(basename "$file")

    # Skip ToastActivity.java and ToastUtils.java
    if [[ "$filename" == "ToastActivity.java" ]] || [[ "$filename" == "ToastUtils.java" ]]; then
        return 0
    fi

    # Check if file contains Toast.makeText
    if ! grep -q "Toast\.makeText" "$file"; then
        return 0
    fi

    echo -e "${YELLOW}Processing:${NC} $filename"

    # Create backup
    cp "$file" "$file.bak"

    local replacements=0

    # Add ToastUtils import if not present
    if ! grep -q "import com.fc.safe.utils.ToastUtils;" "$file"; then
        # Find the last import line and add ToastUtils import after it
        sed -i.tmp '/^import /h; ${x; s/^import .*/&\nimport com.fc.safe.utils.ToastUtils;/; p; x}; /^import /!{x; /./{x; p}; x}' "$file"
        rm -f "$file.tmp"
    fi

    # Replace Toast.makeText patterns with ToastUtils
    # This is a simplified replacement - manual review is recommended

    # Pattern 1: Error messages (failed, error, incorrect, invalid, etc.)
    sed -i.tmp -E '
        s/Toast\.makeText\(([^,]+),\s*getString\((R\.string\.[^,)]+)\),\s*Toast\.LENGTH_(SHORT|LONG)\)\.show\(\)/ToastUtils.showError(\1, getString(\2))/g
        s/Toast\.makeText\(([^,]+),\s*([^,)]+\.getString\([^)]+\)),\s*Toast\.LENGTH_(SHORT|LONG)\)\.show\(\)/ToastUtils.showError(\1, \2)/g
    ' "$file"

    # Pattern 2: Warning messages (empty, no_, nothing, select, etc.)
    sed -i.tmp -E '
        s/Toast\.makeText\(([^,]+),\s*R\.string\.([^,)]*empty[^,)]*),\s*Toast\.LENGTH_(SHORT|LONG)\)\.show\(\)/ToastUtils.showWarning(\1, getString(R.string.\2))/g
        s/Toast\.makeText\(([^,]+),\s*R\.string\.([^,)]*nothing[^,)]*),\s*Toast\.LENGTH_(SHORT|LONG)\)\.show\(\)/ToastUtils.showWarning(\1, getString(R.string.\2))/g
        s/Toast\.makeText\(([^,]+),\s*R\.string\.([^,)]*select[^,)]*),\s*Toast\.LENGTH_(SHORT|LONG)\)\.show\(\)/ToastUtils.showWarning(\1, getString(R.string.\2))/g
    ' "$file"

    # Pattern 3: Info messages (success, saved, copied, etc.) - catch-all
    sed -i.tmp -E '
        s/Toast\.makeText\(([^,]+),\s*R\.string\.([^,)]+),\s*Toast\.LENGTH_(SHORT|LONG)\)\.show\(\)/ToastUtils.showInfo(\1, getString(R.string.\2))/g
        s/Toast\.makeText\(([^,]+),\s*getString\((R\.string\.[^,)]+)\),\s*Toast\.LENGTH_(SHORT|LONG)\)\.show\(\)/ToastUtils.showInfo(\1, getString(\2))/g
    ' "$file"

    # Remove Toast import if no longer needed
    if ! grep -q "Toast\." "$file" || [ "$(grep -o "Toast\." "$file" | wc -l)" -eq "$(grep -o "ToastUtils\." "$file" | wc -l)" ]; then
        sed -i.tmp '/^import android\.widget\.Toast;$/d' "$file"
    fi

    rm -f "$file.tmp"

    # Count replacements
    replacements=$(diff -U 0 "$file.bak" "$file" | grep -c "^[-+]" || echo 0)

    if [ "$replacements" -gt 0 ]; then
        echo -e "  ${GREEN}✓${NC} Replaced Toast calls in $filename"
        TOTAL_FILES=$((TOTAL_FILES + 1))
        TOTAL_REPLACEMENTS=$((TOTAL_REPLACEMENTS + replacements / 2))  # Divide by 2 because diff counts both + and -
    else
        echo -e "  ${YELLOW}⚠${NC} No changes needed for $filename"
    fi

    # Keep backup for manual review
    # rm -f "$file.bak"
}

# Find and process all Java files
echo "Searching for Java files with Toast.makeText..."
echo ""

while IFS= read -r file; do
    process_file "$file"
done < <(find "$SAFE_DIR" -name "*.java" -type f)

echo ""
echo "=================================================="
echo -e "${GREEN}SUMMARY${NC}"
echo "=================================================="
echo "Files processed: $TOTAL_FILES"
echo "Estimated replacements: ~$TOTAL_REPLACEMENTS"
echo ""
echo "Backup files created with .bak extension"
echo "Please review changes before committing!"
echo "=================================================="
