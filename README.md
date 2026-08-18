# Hak Et

Kamerayla sinav / mekik / squat sayan, karsiliginda ekran suresi veren,
sure bitince Instagram + YouTube + WhatsApp'i engelleyen Android uygulamasi.

## Kurallar (Store.kt icinde tek yerden degistirilir)

| Eylem | Kazanc |
|---|---|
| 1 sinav | 2 dakika |
| 2 mekik | 1 dakika |
| 4 squat | 1 dakika |
| 15 sn "diger" hareket | 1 dakika |
| Gun basi hediye | 10 dakika |

- Engelli uygulamalardan biri on plandayken her saniye bakiyeden 1 sn duser.
- Bakiye 0 olunca Instagram / YouTube / WhatsApp acilmaz.
- Bu uygulamalarda toplam 15 dakika gecirilince, **sure kalmis olsa bile**
  5 sinav + 10 mekik + 10 squat yapilmadan acilmaz. Ceza bitince 15 dk sayaci sifirlanir.
- Ana menudeki ACIL DURUM anahtari aciksa engelleme tamamen devre disi.
- Tum sayaclar gece yarisi sifirlanir.

## APK nasil alinir

### Yol 1 - GitHub Actions (bilgisayara hicbir sey kurmadan)
1. Bu klasoru yeni bir GitHub deposuna push et.
2. Depo > Actions sekmesi > "APK Derle" > calismasini bekle (~4 dk).
3. Calisma sayfasinin altindaki **HakEt-apk** artifact'ini indir, zip'ten `app-debug.apk` cikar.

### Yol 2 - Android Studio
1. Klasoru Android Studio ile ac (Gradle wrapper otomatik olusur).
2. Build > Build Bundle(s)/APK(s) > Build APK(s).
3. `app/build/outputs/apk/debug/app-debug.apk`

## Telefona kurulum (realme 12 Pro 5G / realme UI)

1. APK'yi telefona at, kur (Bilinmeyen kaynaklara izin ver).
2. Uygulamayi ac, **Kurulum** bolumundeki 3 maddeyi tamamla:
   - Kamera izni
   - Erisilebilirlik servisi -> Ayarlar > Ek ayarlar > Erisilebilirlik > Indirilen uygulamalar > Hak Et > Ac
   - Diger uygulamalarin ustunde goster
3. realme UI arka planda servisi oldurur. Sunlari yap:
   - Ayarlar > Pil > Uygulama pil kullanimi > Hak Et > **Sinirlama yok / Arka planda calismasina izin ver**
   - Son uygulamalar ekraninda Hak Et karti > kilit ikonu ile kilitle
   - Ayarlar > Uygulamalar > Otomatik baslatma > Hak Et > Ac

## Egzersiz sayimi icin ipuclari
- Telefonu 2-3 metre uzaga, tum vucudunu gorecek sekilde sabitle (yan acidan en iyi sonuc).
- Isikli ortam sart, karanlikta iskelet tespiti bozulur.
- Sayim eksik/fazla ise `Sayac.kt` icindeki aci esiklerini oynat
  (sinav 95/150, mekik 80/125, squat 105/160 derece).

## Dosya haritasi
- `Store.kt` - tum kurallar, gunluk sayaclar, kalori
- `Sayac.kt` - iskelet acilarindan tekrar sayma
- `PozAnaliz.kt` - ML Kit poz tespiti
- `EgzersizActivity.kt` - kamera ekrani
- `AnaActivity.kt` - ana menu, acil tusu, kurulum
- `EngelServisi.kt` - Instagram/YouTube/WhatsApp engelleme
- `EngelActivity.kt` - engel ekrani
