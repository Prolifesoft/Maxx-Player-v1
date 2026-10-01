import re
import glob

replacements = {
    "tab_home": "Anasayfa",
    "tab_movies": "Flim",
    "tab_series": "Dizi",
    "tab_live": "Canlı",
    "tab_favorites": "Favori"
}

files = glob.glob("app/src/main/res/values*/strings.xml")

for file in files:
    with open(file, "r") as f:
        content = f.read()
    
    for key, value in replacements.items():
        if f'name="{key}"' in content:
            content = re.sub(rf'<string name="{key}">.*?</string>', f'<string name="{key}">{value}</string>', content)
        else:
            # If not exists, insert before </resources>
            content = content.replace("</resources>", f'    <string name="{key}">{value}</string>\n</resources>')
            
    with open(file, "w") as f:
        f.write(content)
print("Updated all strings.xml")
