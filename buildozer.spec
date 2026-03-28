[app]

title = 旮旯GAME
package.name = gala_game
package.domain = org.gala
source.dir = .
source.include_exts = py,png,jpg,jpeg,gif,bmp,kv,atlas,db,json,ttf,otf,txt
source.exclude_exts = spec,pyc,pyo,md
version = 1.0.0
requirements = python3,kivy,pillow,aiohttp,async_timeout,aiohappyeyeballs,aiosignal,attrs,frozenlist,multidict,propcache,yarl,idna,chardet,android,pyjnius,openssl
orientation = portrait
fullscreen = 0

android.permissions = INTERNET,WRITE_EXTERNAL_STORAGE,READ_EXTERNAL_STORAGE,READ_MEDIA_IMAGES,READ_MEDIA_VIDEO,READ_MEDIA_AUDIO,CAMERA,RECORD_AUDIO,VIBRATE
android.api = 33
android.ndk = 25b
android.accept_sdk_license = True
android.archs = arm64-v8a,armeabi-v7a
android.minapi = 21
android.allow_backup = False
android.windowsoftinputmode = adjustResize
android.skip_update = False

android.p4a_whitelist = lib/python*/site-packages/aiohttp/*.so,lib/python*/site-packages/multidict/*.so,lib/python*/site-packages/yarl/*.so,lib/python*/site-packages/frozenlist/*.so,lib/python*/site-packages/propcache/*.so,lib/python*/site-packages/aiosignal/*.so,lib/python*/site-packages/aiohappyeyeballs/*.so

p4a.bootstrap = sdl2
p4a.branch = master

[buildozer]
log_level = 2
warn_on_root = 1
