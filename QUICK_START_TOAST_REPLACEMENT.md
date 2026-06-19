# Quick Start: Complete Toast Replacement

## Current Status ✅

**Manually Completed**: 10 files, ~47 Toast calls replaced

### Completed Files:
1. ✅ BackupKeysActivity.java
2. ✅ BackupSecretsActivity.java
3. ✅ FcEntityImporter.java
4. ✅ HomeActivity.java
5. ✅ TotpActivity.java
6. ✅ TotpCard.java
7. ✅ CreateSecretActivity.java
8. ✅ SecretManager.java
9. ✅ ChangePasswordActivity.java
10. ✅ SecretActivity.java

**Remaining**: 51 files, ~224 Toast calls

## Complete Replacement in 3 Steps

### Step 1: Run Automation Script ⚡

```bash
cd /Users/liuchangyong/AndroidStudioProjects/Safe
python3 scripts/replace_toasts.py
```

**What it does:**
- Scans all Java files in `app/src/main/java/com/fc/safe`
- Automatically replaces `Toast.makeText()` with appropriate `ToastUtils` methods
- Adds/removes imports as needed
- Creates `.bak` backup files
- Shows detailed progress and statistics

**Expected Output:**
```
================================================================================
Toast.makeText to ToastUtils Replacement Script
================================================================================

Found 61 Java files to scan

✓ app/src/main/java/com/fc/safe/MainActivity.java: 2 Toast calls replaced
✓ app/src/main/java/com/fc/safe/tx/SignTxActivity.java: 7 Toast calls replaced
...

================================================================================
SUMMARY
================================================================================
Files processed: 51
Total Toast.makeText calls replaced: 224
================================================================================
```

### Step 2: Review Changes 🔍

The script creates `.bak` backup files. Review the changes:

```bash
# Compare original and new version for any file
diff app/src/main/java/com/fc/safe/MainActivity.java.bak \
     app/src/main/java/com/fc/safe/MainActivity.java

# Or use your favorite diff tool
# VS Code: code --diff file.bak file.java
# IntelliJ: idea diff file.bak file.java
```

**What to verify:**
- [ ] Error messages use `ToastUtils.showError()`
- [ ] Warning messages use `ToastUtils.showWarning()`
- [ ] Info messages use `ToastUtils.showInfo()`
- [ ] `getString()` is used for string resources
- [ ] Import statements are correct

### Step 3: Test & Commit ✨

1. **Build the project:**
   ```bash
   ./gradlew assembleDebug
   ```

2. **Run the app and test:**
   - Trigger various toast messages
   - Check ToastActivity shows message history
   - Verify colors: red (errors), yellow (warnings), default (info)
   - Test edge cases (empty lists, errors, etc.)

3. **Clean up backups if satisfied:**
   ```bash
   find app/src/main/java/com/fc/safe -name "*.bak" -delete
   ```

4. **Commit changes:**
   ```bash
   git add -A
   git commit -m "feat: Replace all Toast.makeText with ToastUtils

- Replaced Toast.makeText in 61 files (~271 total calls)
- Categorized messages as error/warning/info
- All messages now display in ToastActivity
- Improved user experience with color-coded messages
- Added ToastUtils import, removed Toast import where unused"
   ```

## Replacement Categories

| Type | Method | Color | Keywords |
|------|--------|-------|----------|
| **Error** | `ToastUtils.showError()` | Red | error, fail, incorrect, invalid, cannot |
| **Warning** | `ToastUtils.showWarning()` | Yellow | warn, empty, no_, nothing, required |
| **Info** | `ToastUtils.showInfo()` | Default | saved, copied, deleted, success |

## Examples

### Before & After

**Error Message:**
```java
// Before
Toast.makeText(this, getString(R.string.failed_to_save_file),
    Toast.LENGTH_SHORT).show();

// After
ToastUtils.showError(this, getString(R.string.failed_to_save_file));
```

**Warning Message:**
```java
// Before
Toast.makeText(this, R.string.no_items_selected,
    Toast.LENGTH_LONG).show();

// After
ToastUtils.showWarning(this, getString(R.string.no_items_selected));
```

**Info Message:**
```java
// Before
Toast.makeText(this, R.string.copied_to_clipboard,
    Toast.LENGTH_SHORT).show();

// After
ToastUtils.showInfo(this, getString(R.string.copied_to_clipboard));
```

## Troubleshooting

### Script Fails to Run
```bash
# Ensure Python 3 is installed
python3 --version

# Make script executable
chmod +x scripts/replace_toasts.py
```

### Build Errors After Replacement
```bash
# Clean and rebuild
./gradlew clean assembleDebug

# Check for missing imports
# Each file should have:
import com.fc.safe.utils.ToastUtils;

# And NOT have (unless used elsewhere):
import android.widget.Toast;
```

### Want to Undo Changes
```bash
# Restore from backups
find app/src/main/java/com/fc/safe -name "*.bak" | while read f; do
    mv "$f" "${f%.bak}"
done

# Or restore from git
git checkout app/src/main/java/com/fc/safe
```

## Alternative: Manual Replacement

If you prefer to complete manually, process files in this order:

**Priority 1** (High-traffic UI):
- MainActivity.java (2 calls)
- SignTxActivity.java (7 calls)
- CashActivity.java (9 calls)
- FcEntityListFragment.java (8 calls)

**Priority 2** (Key functionality):
- KeyInfoManager.java (2 calls)
- MultisignManager.java
- CashManager.java

**Priority 3** (Remaining files)
- All CreateKey*.java files
- All dialog files
- All utility files

## Need Help?

1. **Check the full documentation:**
   ```bash
   cat TOAST_REPLACEMENT_SUMMARY.md
   ```

2. **View automation script source:**
   ```bash
   cat scripts/replace_toasts.py
   ```

3. **Test on a single file first:**
   ```bash
   # Edit replace_toasts.py to process one file
   # Or create a copy of one file and test manually
   ```

## Success Criteria ✅

- [ ] All 61 files processed
- [ ] ~271 Toast.makeText calls replaced
- [ ] Project builds successfully
- [ ] App runs without crashes
- [ ] ToastActivity shows message history
- [ ] Messages display with correct colors
- [ ] No regression in functionality

---
**Ready to start?** Run Step 1 now! 🚀

```bash
cd /Users/liuchangyong/AndroidStudioProjects/Safe
python3 scripts/replace_toasts.py
```
