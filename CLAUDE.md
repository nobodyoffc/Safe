# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Structure

This is an Android cryptocurrency wallet application called "Safe" with a modular architecture:

### Modules
- **app**: Main Android application module containing UI and business logic
- **FC-AJDK**: Core crypto library module providing cryptographic operations, database management, and blockchain functionality

### Key Packages
- `com.fc.safe.*`: Main app package containing activities, fragments, and UI components
- `com.fc.fc_ajdk.*`: Core library with crypto, database, networking, and utility classes
- `com.fc.safe.db.*`: Database managers and data persistence layer
- `com.fc.safe.home.*`: Home screen and main navigation activities
- `com.fc.safe.myKeys.*`: Key management activities (create, import, export keys)
- `com.fc.safe.multisign.*`: Multi-signature transaction functionality
- `com.fc.safe.tx.*`: Transaction creation and signing
- `com.fc.safe.secret.*`: Secret and TOTP management

## Build Commands

### Basic Build Operations
```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK  
./gradlew assembleRelease

# Build both debug and release
./gradlew build

# Clean build artifacts
./gradlew clean

# Install debug build to connected device
./gradlew installDebug
```

### Testing
```bash
# Run unit tests
./gradlew test

# Run unit tests for debug build
./gradlew testDebugUnitTest

# Run instrumentation tests on device
./gradlew connectedAndroidTest

# Run all checks (lint + tests)
./gradlew check
```

### Code Quality
```bash
# Run lint analysis
./gradlew lint

# Run lint and auto-fix issues
./gradlew lintFix

# Run lint on debug variant
./gradlew lintDebug

# Update lint baseline
./gradlew updateLintBaseline
```

## Development Guidelines

### Coding Conventions (from .cursor/rules)
- Add common toolbar with `@+id/toolbar` to each activity layout and setup with `ToolbarUtils.setupToolbar(this, <title>)`
- Apply `@style/ButtonStyle` when creating buttons
- When passing FcEntity objects as parameters or results, convert to JSON string first using `fcEntity.toJson()`

### Database Architecture
The app uses a multi-database system managed by `DatabaseManager`:
- Different databases for different password contexts
- LocalDB and HawkDB implementations for different storage needs
- Automatic database switching when password context changes
- Global FID (identifier) list management in `SafeApplication`

### Key Components
- **MainActivity**: Entry point handling password verification flow
- **SafeApplication**: Global app state, database lifecycle, background timeout management
- **DatabaseManager**: Centralized database management with password-based contexts
- **Configure/ConfigureManager**: App configuration and settings management
- **BackgroundTimeoutManager**: Security feature to lock app when backgrounded

### Cryptographic Features
The FC-AJDK module provides 4 encryption/decryption types:
1. **SymKey**: Symmetric key encryption (32 bytes)
2. **Password**: Password-based encryption (UTF-8, max 64 bytes)  
3. **AsyOneWay**: Asymmetric encryption with generated key pair
4. **AsyTwoWay**: Two-way asymmetric encryption with existing key pairs

### Avatar System
Avatar resources follow naming convention: `avatar_[type]_[number].png`
- Place in `res/drawable` directory
- Type 0-9 for different component types
- Numbers 0-57 for specific components
- Initialize with `AvatarMaker.init(context)` then use `AvatarMaker.makeAvatar(address)`

### Security Considerations
- Password-based database encryption
- Background timeout for security
- Private key protection with cipher encryption
- Multi-signature transaction support
- QR code scanning for secure data import/export

### Testing Structure
- Unit tests in `src/test/`
- Instrumented tests in `src/androidTest/`
- Both modules have test coverage
- Use JUnit 4 for unit testing
- Espresso for UI testing