#!/bin/bash

# Source directory
SOURCE_DIR="FC-AJDK/src/main/java/com/fc/fc_ajdk/feature/avatar/elements"

# Destination directory
DEST_DIR="app/src/main/res/drawable/avatar-base"

# Create destination directory if it doesn't exist
mkdir -p "$DEST_DIR"

# Loop through each type directory (0-9)
for type_dir in "$SOURCE_DIR"/*; do
  if [ -d "$type_dir" ]; then
    type=$(basename "$type_dir")
    
    # Loop through each PNG file in the type directory
    for png_file in "$type_dir"/*.png; do
      if [ -f "$png_file" ]; then
        number=$(basename "$png_file" .png)
        
        # Create the new filename
        new_filename="avatar_${type}_${number}.png"
        
        # Copy the file to the destination with the new name
        cp "$png_file" "$DEST_DIR/$new_filename"
        
        echo "Copied $png_file to $DEST_DIR/$new_filename"
      fi
    done
  fi
done

echo "Avatar resources moved and renamed successfully!" 