from PIL import Image
import os

src = r"E:\qclaw\Lelebox\Lelebox_src\Lelebox.png"
res_dir = r"E:\qclaw\Lelebox\Lelebox_src\app\src\main\res"

# Standard Android launcher icon sizes
densities = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

img = Image.open(src).convert("RGBA")
print(f"Source: {img.size[0]}x{img.size[1]}")

for folder, size in densities.items():
    path = os.path.join(res_dir, folder)
    os.makedirs(path, exist_ok=True)
    
    resized = img.resize((size, size), Image.LANCZOS)
    
    # ic_launcher.png
    png_path = os.path.join(path, "ic_launcher.png")
    resized.save(png_path, "PNG")
    
    # ic_launcher_round.webp
    webp_path = os.path.join(path, "ic_launcher_round.webp")
    resized.save(webp_path, "WEBP", quality=90)
    
    print(f"  {folder}: {size}x{size} -> {png_path} ({os.path.getsize(png_path)}B)")
    print(f"  {folder}: {size}x{size} -> {webp_path} ({os.path.getsize(webp_path)}B)")

print("Done!")
