from pathlib import Path
p = Path(r"D:/ai/carpet-fga/src/main/resources/carpet-fga-addition.mixins.json")
t = p.read_text(encoding="utf-8")
old = "        //#if MC >= 1.18\n        \"PlayerCommandMixin\",\n        //#endif"
new = "        //#if MC >= 1.20\n        \"PlayerCommandMixin\",\n        //#endif"
old2 = old.replace("\n", "\r\n")
if old in t:
    t = t.replace(old, new)
    print("updated lf")
elif old2 in t:
    t = t.replace(old2, new.replace("\n", "\r\n"))
    print("updated crlf")
else:
    print("block not found")
p.write_text(t, encoding="utf-8")
for i, line in enumerate(p.read_text(encoding="utf-8").splitlines(), 1):
    if "PlayerCommand" in line or "FillCommand" in line:
        print(f"{i}:{line}")
