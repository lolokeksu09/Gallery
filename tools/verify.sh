#!/usr/bin/env bash
# Fast checks that need no Android SDK.
#
# The development sandbox cannot reach dl.google.com, so the Android SDK cannot be
# installed there and `gradle assembleDebug` cannot run. These checks catch the
# mistakes that otherwise only surface after a full CI round trip.
set -uo pipefail
cd "$(dirname "$0")/.."

fail=0
note() { printf '  %s\n' "$1"; }
bad() { printf 'FAIL %s\n' "$1"; fail=1; }
ok() { printf 'ok   %s\n' "$1"; }

res=app/src/main/res
kotlin=app/src/main/java

# 1. Every XML resource and the manifest must parse.
if python3 - "$res" app/src/main/AndroidManifest.xml <<'PY'
import sys, pathlib, xml.etree.ElementTree as ET
bad = []
targets = []
for arg in sys.argv[1:]:
    p = pathlib.Path(arg)
    targets += sorted(p.rglob("*.xml")) if p.is_dir() else [p]
for f in targets:
    try:
        ET.parse(f)
    except Exception as e:
        bad.append(f"{f}: {e}")
for b in bad:
    print(b)
sys.exit(1 if bad else 0)
PY
then ok "XML resources and manifest parse"; else bad "malformed XML"; fi

# 2. ?attr/ is an AppCompat theme reference. This project has no AppCompat and uses the
#    platform theme, so aapt2 cannot resolve it. Platform attributes need ?android:attr/.
if grep -rn '?attr/' "$res" 2>/dev/null; then
    bad "?attr/ needs AppCompat; use ?android:attr/ or drop the attribute"
else
    ok "no AppCompat-only attribute references"
fi

# 3. Every R.drawable reference must have a matching resource file.
missing=0
for name in $(grep -rho 'R\.drawable\.[A-Za-z0-9_]*' "$kotlin" 2>/dev/null | sed 's/R\.drawable\.//' | sort -u); do
    if ! ls "$res"/drawable*/"$name".* >/dev/null 2>&1; then
        note "R.drawable.$name has no file under $res/drawable*/"
        missing=1
    fi
done
[ "$missing" -eq 0 ] && ok "every R.drawable reference resolves" || bad "missing drawable resources"

# 4. material-icons-extended is deliberately not a dependency, so only icons shipped in
#    material-icons-core may be referenced. Anything else needs its own vector drawable.
core="AccountBox AccountCircle Add AddCircle ArrowBack ArrowDropDown ArrowForward Build Call
Check CheckCircle Clear Close Create DateRange Delete Done Edit Email ExitToApp Face Favorite
FavoriteBorder Home Info KeyboardArrowDown KeyboardArrowLeft KeyboardArrowRight KeyboardArrowUp
List LocationOn Lock MailOutline Menu MoreVert Notifications Person Phone Place PlayArrow
Refresh Search Send Settings Share ShoppingCart Star ThumbUp Warning"
if grep -rq 'material-icons-extended' app/build.gradle.kts; then
    bad "material-icons-extended is back in app/build.gradle.kts; this check assumes it is absent"
else
    unknown=0
    for name in $(grep -rho 'Icons\.\(Default\|Filled\|AutoMirrored\.Filled\|AutoMirrored\.Default\)\.[A-Za-z0-9_]*' "$kotlin" 2>/dev/null | sed 's/.*\.//' | sort -u); do
        case " $(echo $core) " in
            *" $name "*) ;;
            *) note "Icons.*.$name is not in material-icons-core; add a vector drawable instead"; unknown=1 ;;
        esac
    done
    [ "$unknown" -eq 0 ] && ok "all icon references exist in material-icons-core" || bad "icon outside material-icons-core"
fi

# 5. Cross-file references to the repositories. This codebase has exactly two, both named
#    `repository` at their call sites, so every `repository.name(` must have a matching
#    `fun name(` somewhere in the sources. A silent find-and-replace that never landed shows up
#    here instead of costing a CI round trip.
missing_ref=0
for name in $(grep -rho 'repository\.[a-zA-Z_][a-zA-Z0-9_]*(' "$kotlin" 2>/dev/null | sed 's/repository\.//; s/($//; s/(//' | sort -u); do
    if ! grep -rq "fun $name(" "$kotlin"; then
        note "repository.$name(...) is called but no 'fun $name(' exists"
        missing_ref=1
    fi
done
[ "$missing_ref" -eq 0 ] && ok "every repository call resolves to a declared function" || bad "call to a function that does not exist"

# 6. The offline guarantee: no networking permission or library may appear.
if grep -rn 'android.permission.INTERNET' app/src/main/AndroidManifest.xml | grep -vq 'tools:node="remove"'; then
    bad "INTERNET permission is not marked for removal"
else
    ok "manifest keeps the offline guarantee"
fi

if [ "$fail" -eq 0 ]; then
    echo "verify: all checks passed"
else
    echo "verify: failed; fix the items above before pushing"
fi
exit "$fail"
