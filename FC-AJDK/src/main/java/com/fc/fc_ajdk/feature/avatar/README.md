# Avatar Resources Organization

This document explains how to organize the avatar resources for the AvatarMaker class.

## Resource Naming Convention

The AvatarMaker class expects avatar resources to be organized in the following way:

1. All avatar resources should be placed in the `res/drawable` directory of your Android project.
2. Resource names should follow the pattern: `avatar_[type]_[number].png`
   - `[type]` is the type of avatar component (0-9)
   - `[number]` is the specific component number (0-57)

## Resource Organization

For example, if you have an avatar component that was previously located at `basePath/0/9.png`, it should now be placed in the drawable directory as `avatar_0_9.png`.

## Resource Types

The AvatarMaker class supports the following types of avatar components:

- Type 0: Base image
- Types 1-9: Additional components

## Example Resource Names

Here are some example resource names:

- `avatar_0_9.png` - Base image with number 9
- `avatar_1_10.png` - Component type 1 with number 10
- `avatar_2_11.png` - Component type 2 with number 11

## Migration Instructions

To migrate from file-based avatar creation to resource-based avatar creation:

1. Create the necessary directories in your project's `res/drawable` folder
2. Copy all avatar component images to the appropriate location
3. Rename the files according to the naming convention
4. Initialize the AvatarMaker with your application context:

```java
// In your Application class or main activity
AvatarMaker.init(getApplicationContext());
```

5. Use the new methods to create avatars:

```java
// Create a single avatar
byte[] avatarBytes = AvatarMaker.makeAvatar("address");

// Create multiple avatars
Map<String, byte[]> avatarMap = AvatarMaker.makeAvatars(addressArray);
```

## Legacy Support

The original file-based methods are still available for backward compatibility:

```java
String[] filePaths = AvatarMaker.getAvatars(addressArray, basePath, filePath);
``` 