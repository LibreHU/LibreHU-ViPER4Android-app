# ViPER4Android — fork LibreHU

Fork de [ViPER4Android](https://github.com/likelikeslike/ViPER4Android) (interface Material 3 de ViPER4Android FX)
pour les autoradios Jancar / Autochips AC8257 sous LibreHU. Point de départ : le commit amont `f05926f` (« doc: Add
priv-app SELinux rules note »). Le moteur audio (module ViPERFX_RE) n'est pas modifié ; le fork ajoute l'intégration
LibreHU et un écran pour le processeur audio de l'autoradio.

Ce fichier liste **toutes** les modifications LibreHU par rapport à l'amont, branche par branche. Le `README.md`
d'amont reste celui du projet (plus une section de captures d'écran sur `librehu-service`).

## Branches

| Branche | Contenu | Publiée par LibreHU Apps |
|---|---|---|
| `main` | amont + icône thémable + demande de root groupée | non |
| `vehicle-audio` | `main` (ancien) + écran Audio véhicule (ivi-services de Jancar, puis LibreHU-service) | non (fusionnée dans `librehu-service`) |
| `librehu-service` | `main` + `vehicle-audio` + thème du launcher + signature, versions et CI LibreHU | **oui** (édition LibreHU-service) |

Les modifications se font sur `main` (ou `vehicle-audio` pour l'écran audio), puis `main` est fusionnée dans
`librehu-service` (fusion, jamais de réécriture d'historique).

## Modifications sur `main`

| Fichier | Modification |
|---|---|
| `app/src/main/AndroidManifest.xml` | méta-donnée `org.librehu.themed_icon` → `@drawable/ic_launcher_monochrome` : LibreHU Launcher dessine l'icône dans les couleurs de son thème |
| `app/src/main/AndroidManifest.xml` | récepteur `librehu.RootRequestReceiver` (action `org.librehu.action.REQUEST_ROOT`) |
| `app/src/main/java/com/llsl/viper4android/librehu/RootRequestReceiver.kt` | root demandé une fois pour toutes (voir plus bas) |
| `README_LIBREHU.md` | ce fichier |

Le suivi du thème du launcher avait été essayé sur `main` (`2044b76`) puis retiré (`b45f139`) : il vit sur
`librehu-service`, avec le reste de l'intégration.

## Modifications sur `vehicle-audio` (fusionnées dans `librehu-service`)

| Fichier | Modification |
|---|---|
| `app/src/main/java/com/llsl/viper4android/headunit/VehicleAudioScreen.kt` | écran **Audio véhicule** : volume maître et sourdine, balance / fader, graves / médiums / aigus (pas de 2 dB), loudness, sortie et niveau caisson (−5 à +7 dB), interrupteur de l'ampli externe (sortie REM), recharger depuis l'autoradio, valeurs par défaut |
| `…/headunit/VehicleAudioViewModel.kt` | état de l'écran, lecture / écriture des réglages par le service ; bandes d'égaliseur inexistantes de la ROHM BD37534 masquées |
| `…/headunit/LibreHuClient.kt` | liaison à LibreHU-service (`org.librehu.service`, AIDL) ; d'abord ivi-services de Jancar, remplacé par LibreHU-service |
| `app/src/main/aidl/org/librehu/service/ILibreHuService.aidl`, `ILibreHuCallback.aidl` | copie de l'API publique de LibreHU-service |
| `…/ui/navigation/ViperNavigation.kt`, `…/ui/screens/main/MainScreen.kt` | entrée « Audio véhicule » dans l'écran principal et sa route |
| `app/src/main/res/values/strings_headunit.xml` | textes de l'écran (messages : service absent, service ne pilotant pas le matériel, contact coupé) |

Le processeur audio de l'autoradio est **après** le mixeur d'Android : ses réglages s'appliquent à toutes les sources,
alors que les effets ViPER ne traitent que l'audio d'Android.

## Modifications propres à `librehu-service`

| Fichier | Modification |
|---|---|
| `…/ui/theme/LibreHuTheme.kt`, `…/ui/theme/Theme.kt` | clair / sombre et couleur d'accent de LibreHU Launcher (`content://org.librehu.launcher.theme/theme`, diffusion `org.librehu.action.THEME_CHANGED`), donc sombre avec les phares quand le launcher est en automatique |
| `app/build.gradle.kts` | signature par la clé commune des applis LibreHU (`keystore/librehu.jks`), `versionCode` = nombre de commits (lu par `providers.exec`, compatible cache de configuration) |
| `keystore/` | clé de signature commune et son README |
| `.github/workflows/build.yml` | CI : APK signé, pré-version roulante par branche avec `release.txt` (version lue par LibreHU OTA), 15 dernières versions gardées |
| `README.md`, `docs/screenshots/main.png` | section de captures d'écran |
| `.gitignore` | sorties de build |

## Root : demandé une fois pour toutes

Les fonctions root de ViPER (journal de débogage, etc.) passent par SuperSU, qui accorde le root appli par appli.
L'assistant de premier démarrage de LibreHU Launcher (« Autoriser le root pour toutes les applis LibreHU ») interroge
chaque appli LibreHU l'une après l'autre : `RootRequestReceiver`, sur `org.librehu.action.REQUEST_ROOT` envoyé à ce
paquet, lance une fois `su -c id` (SuperSU affiche la demande de ViPER, à accorder avec « Se souvenir »), puis répond
`org.librehu.action.ROOT_RESULT` (extras `package`, `granted`) au paquet indiqué dans `reply_to`. Rien d'autre que
`id` n'est lancé ; une seconde demande dans les 20 s est ignorée.

## Suivre l'amont

```
git remote add upstream https://github.com/likelikeslike/ViPER4Android
git fetch upstream
git checkout main && git merge upstream/main
git checkout librehu-service && git merge main
```

Licence : celle du projet amont (voir `LICENSE`).
