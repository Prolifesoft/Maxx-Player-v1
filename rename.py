import sys
import json

# metadata.json
meta_content = open("metadata.json").read()
meta = json.loads(meta_content)
meta["name"] = "Maxi Panel"
open("metadata.json", "w").write(json.dumps(meta, indent=2))

# strings.xml
strings_content = open("app/src/main/res/values/strings.xml").read()
strings_content = strings_content.replace('name="app_name">IPTV İzle', 'name="app_name">Maxi Panel')
strings_content = strings_content.replace('name="app_name">My Application', 'name="app_name">Maxi Panel')
open("app/src/main/res/values/strings.xml", "w").write(strings_content)

# strings.xml (tr)
try:
    strings_tr_content = open("app/src/main/res/values-tr/strings.xml").read()
    strings_tr_content = strings_tr_content.replace('name="app_name">IPTV İzle', 'name="app_name">Maxi Panel')
    open("app/src/main/res/values-tr/strings.xml", "w").write(strings_tr_content)
except:
    pass

# settings.gradle.kts
settings_content = open("settings.gradle.kts").read()
settings_content = settings_content.replace('rootProject.name = "My Application"', 'rootProject.name = "Maxi Panel"')
settings_content = settings_content.replace('rootProject.name = "IPTV İzle"', 'rootProject.name = "Maxi Panel"')
open("settings.gradle.kts", "w").write(settings_content)

