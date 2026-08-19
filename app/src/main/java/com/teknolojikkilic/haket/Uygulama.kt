package com.teknolojikkilic.haket

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.atan2
import kotlinx.coroutines.delay

// ===================== App =====================

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
    }
}

// ===================== Mod =====================

enum class Mod(val etiket: String) {
    SINAV("Sinav"),
    MEKIK("Mekik"),
    SQUAT("Squat"),
    DIGER("Diger"),
    CEZA("Ceza")
}

// ===================== Store =====================

/**
 * Tum kurallar ve gunluk sayaclar burada.
 * Odul/ceza degerlerini degistirmek istersen sadece asagidaki sabitleri degistir.
 */
object Store {

    // ---------- KURALLAR ----------
    const val GUN_BASI_SANIYE = 10 * 60      // gun basinda 10 dakika hediye
    const val SINAV_SANIYE = 120             // 1 sinav  = 2 dk
    const val MEKIK_SANIYE = 30              // 2 mekik  = 1 dk
    const val SQUAT_SANIYE = 15              // 4 squat  = 1 dk
    const val DIGER_BLOK_SN = 15             // 15 sn hareket
    const val DIGER_ODUL_SN = 60             // = 1 dk
    const val KULLANIM_LIMITI_SN = 15 * 60   // 15 dk sonra ceza devreye girer
    const val CEZA_SINAV = 5
    const val CEZA_MEKIK = 10
    const val CEZA_SQUAT = 10

    // kalori katsayilari (70 kg referans)
    private const val KCAL_SINAV = 0.50f
    private const val KCAL_MEKIK = 0.28f
    private const val KCAL_SQUAT = 0.32f
    private const val KCAL_DIGER_DK = 4.9f

    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        if (!::sp.isInitialized) {
            sp = ctx.applicationContext.getSharedPreferences("haket", Context.MODE_PRIVATE)
        }
        gunKontrol()
    }

    private fun bugun(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    /** Yeni gune gecildiyse tum gunluk sayaclari sifirlar. */
    @Synchronized
    fun gunKontrol() {
        if (!::sp.isInitialized) return
        if (sp.getString("gun", "") != bugun()) {
            sp.edit()
                .putString("gun", bugun())
                .putInt("bakiye", GUN_BASI_SANIYE)
                .putInt("sinav", 0)
                .putInt("mekik", 0)
                .putInt("squat", 0)
                .putInt("digerSn", 0)
                .putFloat("kalori", 0f)
                .putInt("kullanim", 0)
                .putBoolean("ceza", false)
                .putInt("cezaSinav", 0)
                .putInt("cezaMekik", 0)
                .putInt("cezaSquat", 0)
                .apply()
        }
    }

    private fun i(k: String, d: Int = 0) = sp.getInt(k, d)
    private fun setI(k: String, v: Int) = sp.edit().putInt(k, v).apply()

    var bakiye: Int
        get() = i("bakiye", GUN_BASI_SANIYE)
        set(v) = setI("bakiye", v.coerceAtLeast(0))

    var sinav: Int
        get() = i("sinav")
        set(v) = setI("sinav", v)
    var mekik: Int
        get() = i("mekik")
        set(v) = setI("mekik", v)
    var squat: Int
        get() = i("squat")
        set(v) = setI("squat", v)
    var digerSn: Int
        get() = i("digerSn")
        set(v) = setI("digerSn", v)

    var kullanim: Int
        get() = i("kullanim")
        set(v) = setI("kullanim", v)

    var cezaAktif: Boolean
        get() = sp.getBoolean("ceza", false)
        set(v) = sp.edit().putBoolean("ceza", v).apply()

    var cezaSinav: Int
        get() = i("cezaSinav")
        set(v) = setI("cezaSinav", v)
    var cezaMekik: Int
        get() = i("cezaMekik")
        set(v) = setI("cezaMekik", v)
    var cezaSquat: Int
        get() = i("cezaSquat")
        set(v) = setI("cezaSquat", v)

    var acil: Boolean
        get() = sp.getBoolean("acil", false)
        set(v) = sp.edit().putBoolean("acil", v).apply()

    var kilo: Int
        get() = i("kilo", 70).coerceIn(30, 200)
        set(v) = setI("kilo", v.coerceIn(30, 200))

    var kalori: Float
        get() = sp.getFloat("kalori", 0f)
        set(v) = sp.edit().putFloat("kalori", v).apply()

    private fun katsayi() = kilo / 70f

    /** Bir tekrar tamamlandiginda cagrilir: sayac + sure + kalori + ceza ilerlemesi. */
    @Synchronized
    fun tekrarEkle(mod: Mod) {
        gunKontrol()
        val k = katsayi()
        when (mod) {
            Mod.SINAV -> { sinav += 1; bakiye += SINAV_SANIYE; kalori += KCAL_SINAV * k }
            Mod.MEKIK -> { mekik += 1; bakiye += MEKIK_SANIYE; kalori += KCAL_MEKIK * k }
            Mod.SQUAT -> { squat += 1; bakiye += SQUAT_SANIYE; kalori += KCAL_SQUAT * k }
            else -> return
        }
        if (cezaAktif) {
            when (mod) {
                Mod.SINAV -> cezaSinav += 1
                Mod.MEKIK -> cezaMekik += 1
                Mod.SQUAT -> cezaSquat += 1
                else -> {}
            }
            if (cezaBitti()) cezaTamamla()
        }
    }

    fun cezaBitti() =
        cezaSinav >= CEZA_SINAV && cezaMekik >= CEZA_MEKIK && cezaSquat >= CEZA_SQUAT

    /** "Diger" modunda gecen aktif saniyeler icin kalori. */
    @Synchronized
    fun digerSaniyeEkle(sn: Int) {
        if (sn <= 0) return
        gunKontrol()
        digerSn += sn
        kalori += (KCAL_DIGER_DK / 60f) * sn * katsayi()
    }

    /** 15 saniyelik her blok icin 1 dakika. */
    @Synchronized
    fun digerOdul() {
        gunKontrol()
        bakiye += DIGER_ODUL_SN
    }

    @Synchronized
    fun harca(sn: Int) {
        gunKontrol()
        bakiye = (bakiye - sn).coerceAtLeast(0)
    }

    @Synchronized
    fun kullanimEkle(sn: Int) {
        gunKontrol()
        kullanim += sn
    }

    @Synchronized
    fun cezaBaslat() {
        cezaAktif = true
        cezaSinav = 0
        cezaMekik = 0
        cezaSquat = 0
    }

    @Synchronized
    fun cezaTamamla() {
        cezaAktif = false
        cezaSinav = 0
        cezaMekik = 0
        cezaSquat = 0
        kullanim = 0   // 15 dakikalik sayac sifirlanir
    }

    fun sureMetni(): String {
        val t = bakiye
        return String.format(Locale.getDefault(), "%02d:%02d", t / 60, t % 60)
    }
}

// ===================== Sayac =====================

data class Sonuc(val yeniTekrar: Int = 0, val yeniAktifSaniye: Int = 0)

/**
 * Iskelet noktalarindan aci hesaplayip tekrar sayan basit durum makinesi.
 * Esik degerleri asagida, isine gore oynayabilirsin.
 */
class Sayac(private val mod: Mod) {

    var tekrar = 0
        private set
    var mesaj = "Telefonu sabitle, tum vucudun goruntuye girsin"
        private set

    private var faz = false                 // "asagi/yukari" durumu
    private var sonVektor: List<Float>? = null
    private var hareketliMs = 0L
    private var sonZaman = 0L
    private var verilenSaniye = 0

    fun sifirla() {
        tekrar = 0
        faz = false
        hareketliMs = 0L
        sonZaman = 0L
        verilenSaniye = 0
        sonVektor = null
    }

    fun isle(pose: Pose): Sonuc = when (mod) {
        Mod.SINAV -> sinav(pose)
        Mod.MEKIK -> mekik(pose)
        Mod.SQUAT -> squat(pose)
        Mod.DIGER -> diger(pose)
        Mod.CEZA -> Sonuc()
    }

    // ---------------- egzersizler ----------------

    private fun sinav(p: Pose): Sonuc {
        val aci = ortAci(
            p, PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST,
            PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST
        ) ?: return gorunmuyor()

        if (aci < 95) {
            faz = true
            mesaj = "Simdi yukari it"
        } else if (aci > 150) {
            if (faz) {
                faz = false
                tekrar++
                mesaj = "Guzel! Tekrar in"
                return Sonuc(yeniTekrar = 1)
            }
            mesaj = "Asagi in (dirsegini buk)"
        } else {
            mesaj = "Devam..."
        }
        return Sonuc()
    }

    private fun mekik(p: Pose): Sonuc {
        // omuz - kalca - diz acisi
        val aci = ortAci(
            p, PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE,
            PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE
        ) ?: return gorunmuyor()

        if (aci < 80) {
            faz = true
            mesaj = "Simdi geri yat"
        } else if (aci > 125) {
            if (faz) {
                faz = false
                tekrar++
                mesaj = "Guzel! Tekrar kalk"
                return Sonuc(yeniTekrar = 1)
            }
            mesaj = "Govdeni yukari kaldir"
        } else {
            mesaj = "Devam..."
        }
        return Sonuc()
    }

    private fun squat(p: Pose): Sonuc {
        val aci = ortAci(
            p, PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE,
            PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE
        ) ?: return gorunmuyor()

        if (aci < 105) {
            faz = true
            mesaj = "Simdi kalk"
        } else if (aci > 160) {
            if (faz) {
                faz = false
                tekrar++
                mesaj = "Guzel! Tekrar cok"
                return Sonuc(yeniTekrar = 1)
            }
            mesaj = "Cokmeye basla"
        } else {
            mesaj = "Devam..."
        }
        return Sonuc()
    }

    /** "Diger": kisi goruntude ve hareket ediyorsa gecen sureyi sayar. */
    private fun diger(p: Pose): Sonuc {
        val simdi = System.currentTimeMillis()
        val noktalar = listOf(
            PoseLandmark.NOSE, PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST,
            PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE,
            PoseLandmark.LEFT_KNEE, PoseLandmark.RIGHT_KNEE
        ).mapNotNull { p.getPoseLandmark(it) }.filter { it.inFrameLikelihood > 0.4f }

        if (noktalar.size < 4) {
            sonZaman = simdi
            sonVektor = null
            mesaj = "Kamera seni gormuyor"
            return Sonuc()
        }

        val vektor = noktalar.flatMap { listOf(it.position.x, it.position.y) }
        val onceki = sonVektor
        sonVektor = vektor

        var hareket = 0f
        if (onceki != null && onceki.size == vektor.size) {
            for (idx in vektor.indices) hareket += abs(vektor[idx] - onceki[idx])
            hareket /= vektor.size
        }

        val dt = if (sonZaman == 0L) 0L else (simdi - sonZaman)
        sonZaman = simdi

        if (hareket > 2.5f) {
            hareketliMs += dt
            mesaj = "Hareket sayiliyor"
        } else {
            mesaj = "Durma, hareket et"
        }

        val toplamSaniye = (hareketliMs / 1000L).toInt()
        val fark = toplamSaniye - verilenSaniye
        verilenSaniye = toplamSaniye
        return Sonuc(yeniAktifSaniye = fark.coerceAtLeast(0))
    }

    private fun gorunmuyor(): Sonuc {
        mesaj = "Vucudun tam gorunmuyor - telefonu geri cek"
        return Sonuc()
    }

    // ---------------- yardimcilar ----------------

    private fun ortAci(
        p: Pose,
        s1: Int, s2: Int, s3: Int,
        g1: Int, g2: Int, g3: Int
    ): Double? {
        val sol = aci(p.getPoseLandmark(s1), p.getPoseLandmark(s2), p.getPoseLandmark(s3))
        val sag = aci(p.getPoseLandmark(g1), p.getPoseLandmark(g2), p.getPoseLandmark(g3))
        return when {
            sol != null && sag != null -> (sol + sag) / 2.0
            else -> sol ?: sag
        }
    }

    private fun aci(a: PoseLandmark?, b: PoseLandmark?, c: PoseLandmark?): Double? {
        if (a == null || b == null || c == null) return null
        if (a.inFrameLikelihood < 0.4f || b.inFrameLikelihood < 0.4f || c.inFrameLikelihood < 0.4f) return null
        val ap = a.position
        val bp = b.position
        val cp = c.position
        var d = Math.toDegrees(
            (atan2(cp.y - bp.y, cp.x - bp.x) - atan2(ap.y - bp.y, ap.x - bp.x)).toDouble()
        )
        d = abs(d)
        if (d > 180.0) d = 360.0 - d
        return d
    }
}

// ===================== PozAnaliz =====================

class PozAnaliz(private val onPoz: (Pose) -> Unit) : ImageAnalysis.Analyzer {

    private val dedektor = PoseDetection.getClient(
        PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
    )

    @ExperimentalGetImage
    override fun analyze(image: ImageProxy) {
        val media = image.image
        if (media == null) {
            image.close()
            return
        }
        val girdi = InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees)
        dedektor.process(girdi)
            .addOnSuccessListener { onPoz(it) }
            .addOnCompleteListener { image.close() }
    }
}

// ===================== Tema =====================

private val renkler = darkColorScheme(
    primary = Color(0xFF12B886),
    onPrimary = Color(0xFF00160F),
    secondary = Color(0xFF4DABF7),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE9ECEF),
    surface = Color(0xFF161B22),
    onSurface = Color(0xFFE9ECEF),
    error = Color(0xFFFF6B6B)
)

@Composable
fun HakEtTema(icerik: @Composable () -> Unit) {
    MaterialTheme(colorScheme = renkler, content = icerik)
}

// ===================== AnaActivity =====================

class AnaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        setContent { HakEtTema { AnaEkran() } }
    }
}

@Composable
private fun AnaEkran() {
    val ctx = LocalContext.current
    var tik by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            Store.gunKontrol()
            tik++
        }
    }

    val kameraIzin = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { tik++ }

    // tik'e bagli okuma -> her saniye yeniden cizilir
    val bakiye = remember(tik) { Store.bakiye }
    val kalori = remember(tik) { Store.kalori }
    val ceza = remember(tik) { Store.cezaAktif }
    val acil = remember(tik) { Store.acil }
    val kullanim = remember(tik) { Store.kullanim }

    val kameraVar = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    val erisimVar = EngelServisi.aktifMi(ctx)
    val ustteVar = Settings.canDrawOverlays(ctx)

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("HAK ET", fontSize = 26.sp, fontWeight = FontWeight.Black)

            // ---- SURE ----
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("Kalan ekran suren", fontSize = 13.sp, color = Color(0xFF9AA4B2))
                    Text(
                        Store.sureMetni(),
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Black,
                        color = if (bakiye > 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        String.format(Locale.getDefault(), "Bugun yakilan: %.0f kcal", kalori),
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Sinav ${Store.sinav} - Mekik ${Store.mekik} - Squat ${Store.squat} - Diger ${Store.digerSn} sn",
                        fontSize = 13.sp, color = Color(0xFF9AA4B2)
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (kullanim.toFloat() / Store.KULLANIM_LIMITI_SN).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "15 dk sinirina kalan: ${((Store.KULLANIM_LIMITI_SN - kullanim).coerceAtLeast(0)) / 60} dk",
                        fontSize = 12.sp, color = Color(0xFF9AA4B2)
                    )
                }
            }

            if (ceza) {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3A1113))
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("CEZA AKTIF", fontWeight = FontWeight.Bold, color = Color(0xFFFF8787))
                        Text(
                            "Kalan: ${Store.CEZA_SINAV - Store.cezaSinav} sinav, " +
                                    "${Store.CEZA_MEKIK - Store.cezaMekik} mekik, " +
                                    "${Store.CEZA_SQUAT - Store.cezaSquat} squat",
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { basla(ctx, Mod.CEZA) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Cezayi tamamla")
                        }
                    }
                }
            }

            // ---- EGZERSIZ MENUSU ----
            Text("Egzersiz sec", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EgzersizButon("Sinav", "1 = 2 dk", Modifier.weight(1f)) { basla(ctx, Mod.SINAV) }
                EgzersizButon("Mekik", "2 = 1 dk", Modifier.weight(1f)) { basla(ctx, Mod.MEKIK) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EgzersizButon("Squat", "4 = 1 dk", Modifier.weight(1f)) { basla(ctx, Mod.SQUAT) }
                EgzersizButon("Diger", "15 sn = 1 dk", Modifier.weight(1f)) { basla(ctx, Mod.DIGER) }
            }

            // ---- ACIL ----
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("ACIL DURUM", fontWeight = FontWeight.Bold)
                        Text(
                            if (acil) "Engelleme KAPALI - uygulamalar serbest"
                            else "Engelleme aktif",
                            fontSize = 12.sp,
                            color = if (acil) Color(0xFFFFA94D) else Color(0xFF9AA4B2)
                        )
                    }
                    Switch(checked = acil, onCheckedChange = { Store.acil = it; tik++ })
                }
            }

            // ---- KURULUM ----
            Text("Kurulum", fontWeight = FontWeight.Bold)
            Kurulum("Kamera izni", kameraVar) {
                kameraIzin.launch(Manifest.permission.CAMERA)
            }
            Kurulum("Erisilebilirlik servisi (engelleme)", erisimVar) {
                ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            Kurulum("Diger uygulamalarin ustunde goster", ustteVar) {
                ctx.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${ctx.packageName}")
                    )
                )
            }

            // ---- KILO ----
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Kilon: ${Store.kilo} kg", Modifier.weight(1f))
                    OutlinedButton(onClick = { Store.kilo = Store.kilo - 1; tik++ }) { Text("-") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { Store.kilo = Store.kilo + 1; tik++ }) { Text("+") }
                }
            }

            Text(
                "Kalori degerleri tahminidir. Kamera sayimi icin telefonu 2-3 metre uzaga, " +
                        "tum vucudunu gorecek sekilde sabitle.",
                fontSize = 11.sp, color = Color(0xFF6C757D)
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EgzersizButon(baslik: String, alt: String, modifier: Modifier, tikla: () -> Unit) {
    Button(onClick = tikla, modifier = modifier.height(74.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(baslik, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(alt, fontSize = 11.sp)
        }
    }
}

@Composable
private fun Kurulum(baslik: String, tamam: Boolean, tikla: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (tamam) "OK" else "!", fontWeight = FontWeight.Black,
                color = if (tamam) MaterialTheme.colorScheme.primary else Color(0xFFFFA94D))
            Spacer(Modifier.width(12.dp))
            Text(baslik, Modifier.weight(1f), fontSize = 14.sp)
            if (!tamam) TextButton(onClick = tikla) { Text("Ac") }
        }
    }
}

private fun basla(ctx: Context, mod: Mod) {
    ctx.startActivity(
        Intent(ctx, EgzersizActivity::class.java).putExtra(EgzersizActivity.EXTRA_MOD, mod.name)
    )
}

// ===================== EgzersizActivity =====================

class EgzersizActivity : ComponentActivity() {

    companion object {
        const val EXTRA_MOD = "mod"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        val mod = runCatching {
            Mod.valueOf(intent.getStringExtra(EXTRA_MOD) ?: Mod.SINAV.name)
        }.getOrDefault(Mod.SINAV)

        setContent {
            HakEtTema {
                EgzersizEkran(mod) { finish() }
            }
        }
    }
}

private class CezaAdimi(
    val mod: Mod,
    val hedef: Int,
    val yapilan: () -> Int
)

private val CEZA_ADIMLARI = listOf(
    CezaAdimi(Mod.SINAV, Store.CEZA_SINAV) { Store.cezaSinav },
    CezaAdimi(Mod.MEKIK, Store.CEZA_MEKIK) { Store.cezaMekik },
    CezaAdimi(Mod.SQUAT, Store.CEZA_SQUAT) { Store.cezaSquat }
)

@Composable
private fun EgzersizEkran(mod: Mod, bitir: () -> Unit) {
    val ctx = LocalContext.current
    var izinVar by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    val izinIste = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { izinVar = it }

    LaunchedEffect(Unit) { if (!izinVar) izinIste.launch(Manifest.permission.CAMERA) }

    if (!izinVar) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Kamera izni gerekiyor", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { izinIste.launch(Manifest.permission.CAMERA) }) { Text("Izin ver") }
                TextButton(onClick = bitir) { Text("Geri") }
            }
        }
        return
    }

    val cezaModu = mod == Mod.CEZA
    var adim by remember { mutableIntStateOf(if (cezaModu) ilkEksikAdim() else 0) }
    val aktifMod = if (cezaModu) CEZA_ADIMLARI[adim].mod else mod

    var tekrar by remember { mutableIntStateOf(0) }
    var mesaj by remember { mutableStateOf("Hazirlan...") }
    var kazanilan by remember { mutableIntStateOf(0) }
    var onKamera by remember { mutableStateOf(true) }

    val sayac = remember(aktifMod) { Sayac(aktifMod) }
    var digerBiriken by remember(aktifMod) { mutableIntStateOf(0) }

    LaunchedEffect(aktifMod) { tekrar = 0 }

    Box(Modifier.fillMaxSize()) {

        Kamera(
            onKamera = onKamera,
            onPoz = { poz ->
                val s = sayac.isle(poz)
                mesaj = sayac.mesaj
                if (s.yeniTekrar > 0) {
                    Store.tekrarEkle(aktifMod)
                    tekrar = sayac.tekrar
                    kazanilan += when (aktifMod) {
                        Mod.SINAV -> Store.SINAV_SANIYE
                        Mod.MEKIK -> Store.MEKIK_SANIYE
                        Mod.SQUAT -> Store.SQUAT_SANIYE
                        else -> 0
                    }
                    if (cezaModu) {
                        if (Store.cezaBitti()) {
                            bitir()
                        } else if (CEZA_ADIMLARI[adim].yapilan() >= CEZA_ADIMLARI[adim].hedef &&
                            adim < CEZA_ADIMLARI.lastIndex
                        ) {
                            adim += 1
                        }
                    }
                }
                if (s.yeniAktifSaniye > 0) {
                    Store.digerSaniyeEkle(s.yeniAktifSaniye)
                    digerBiriken += s.yeniAktifSaniye
                    while (digerBiriken >= Store.DIGER_BLOK_SN) {
                        digerBiriken -= Store.DIGER_BLOK_SN
                        Store.digerOdul()
                        kazanilan += Store.DIGER_ODUL_SN
                    }
                }
            }
        )

        // ust bilgi
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Color(0xCC000000))
                .padding(16.dp)
        ) {
            Text(
                if (cezaModu) "CEZA - ${aktifMod.etiket}" else aktifMod.etiket,
                fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color.White
            )
            if (cezaModu) {
                Text(
                    "Sinav ${Store.cezaSinav}/${Store.CEZA_SINAV} - " +
                            "Mekik ${Store.cezaMekik}/${Store.CEZA_MEKIK} - " +
                            "Squat ${Store.cezaSquat}/${Store.CEZA_SQUAT}",
                    color = Color(0xFFFFA94D), fontSize = 13.sp
                )
            }
            Text("Toplam sure: ${Store.sureMetni()}", color = Color(0xFF9AA4B2), fontSize = 13.sp)
        }

        // alt panel
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xCC000000))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (aktifMod == Mod.DIGER) {
                Text("${Store.digerSn} sn", fontSize = 54.sp, fontWeight = FontWeight.Black, color = Color.White)
            } else {
                Text("$tekrar", fontSize = 64.sp, fontWeight = FontWeight.Black, color = Color.White)
            }
            Text(mesaj, color = Color(0xFF12B886), fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Text("+${kazanilan / 60} dk ${kazanilan % 60} sn kazandin", color = Color.White, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { onKamera = !onKamera }) { Text("Kamera cevir") }
                Button(onClick = bitir) { Text(if (cezaModu) "Vazgec" else "Bitir") }
            }
        }
    }
}

private fun ilkEksikAdim(): Int {
    for (idx in CEZA_ADIMLARI.indices) {
        if (CEZA_ADIMLARI[idx].yapilan() < CEZA_ADIMLARI[idx].hedef) return idx
    }
    return CEZA_ADIMLARI.lastIndex
}

@Composable
private fun Kamera(onKamera: Boolean, onPoz: (Pose) -> Unit) {
    val ctx = LocalContext.current
    val yasam = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val executor = remember { Executors.newSingleThreadExecutor() }
    // analiz thread'i her zaman EN GUNCEL callback'i cagirsin
    val guncelOnPoz by rememberUpdatedState(onPoz)

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

    LaunchedEffect(onKamera) {
        val future = ProcessCameraProvider.getInstance(ctx)
        future.addListener({
            runCatching {
                val provider = future.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analiz = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build().also { it.setAnalyzer(executor, PozAnaliz { poz -> guncelOnPoz(poz) }) }

                val secici = CameraSelector.Builder()
                    .requireLensFacing(
                        if (onKamera) CameraSelector.LENS_FACING_FRONT
                        else CameraSelector.LENS_FACING_BACK
                    ).build()

                provider.unbindAll()
                provider.bindToLifecycle(yasam, secici, preview, analiz)
            }
        }, ContextCompat.getMainExecutor(ctx))
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(ctx).get().unbindAll() }
            executor.shutdown()
        }
    }
}

// ===================== EngelActivity =====================

/** Instagram/YouTube/WhatsApp acildiginda karsina cikan ekran. */
class EngelActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        val sebep = intent.getStringExtra("sebep") ?: "SURE"
        setContent { HakEtTema { EngelEkran(sebep) { finish() } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

@Composable
private fun EngelEkran(sebep: String, kapat: () -> Unit) {
    val ctx = LocalContext.current
    val ceza = sebep == "CEZA"

    Surface(Modifier.fillMaxSize(), color = Color(0xFF14060A)) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (ceza) "15 DAKIKA DOLDU" else "SUREN BITTI",
                fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color(0xFFFF6B6B)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                if (ceza)
                    "Devam etmek icin ${Store.CEZA_SINAV} sinav, ${Store.CEZA_MEKIK} mekik ve " +
                            "${Store.CEZA_SQUAT} squat yapman gerekiyor."
                else
                    "Instagram, YouTube ve WhatsApp kapali. Sure kazanmak icin egzersiz yap.",
                color = Color(0xFFE9ECEF), fontSize = 15.sp
            )
            Spacer(Modifier.height(28.dp))

            if (ceza) {
                Button(
                    onClick = {
                        ctx.startActivity(
                            Intent(ctx, EgzersizActivity::class.java)
                                .putExtra(EgzersizActivity.EXTRA_MOD, Mod.CEZA.name)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        kapat()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Cezayi yapmaya basla") }
            } else {
                listOf(Mod.SINAV, Mod.MEKIK, Mod.SQUAT, Mod.DIGER).forEach { m ->
                    Button(
                        onClick = {
                            ctx.startActivity(
                                Intent(ctx, EgzersizActivity::class.java)
                                    .putExtra(EgzersizActivity.EXTRA_MOD, m.name)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                            kapat()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) { Text(m.etiket) }
                }
            }

            Spacer(Modifier.height(20.dp))
            TextButton(onClick = {
                ctx.startActivity(
                    Intent(ctx, AnaActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                kapat()
            }) { Text("Ana menu", color = Color(0xFF9AA4B2)) }
        }
    }
}

// ===================== EngelServisi =====================

/**
 * Instagram / YouTube / WhatsApp on planda mi diye bakar.
 * On plandaysa her saniye bakiyeden 1 sn duser, kullanim sayacini artirir.
 * Bakiye bittiyse veya 15 dk asildiysa uygulamadan atar.
 */
class EngelServisi : AccessibilityService() {

    companion object {
        val ENGELLI = setOf(
            "com.instagram.android",
            "com.google.android.youtube",
            "com.whatsapp"
        )

        fun aktifMi(ctx: Context): Boolean {
            val s = Settings.Secure.getString(
                ctx.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return s.contains("${ctx.packageName}/${EngelServisi::class.java.name}") ||
                    s.contains("${ctx.packageName}/.${EngelServisi::class.java.simpleName}")
        }
    }

    private val h = Handler(Looper.getMainLooper())
    private var aktifPaket: String? = null
    private var sonEngelMs = 0L

    private val tik = object : Runnable {
        override fun run() {
            saniyeGecti()
            h.postDelayed(this, 1000L)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Store.init(this)
        h.removeCallbacks(tik)
        h.post(tik)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val paket = event.packageName?.toString() ?: return
        if (paket == packageName) return

        aktifPaket = if (paket in ENGELLI) paket else null
        if (aktifPaket != null) kontrol()
    }

    private fun saniyeGecti() {
        if (aktifPaket == null) return
        Store.gunKontrol()
        if (Store.acil) return
        Store.kullanimEkle(1)
        Store.harca(1)
        kontrol()
    }

    private fun kontrol() {
        if (Store.acil) return
        val sebep = when {
            Store.cezaAktif -> "CEZA"
            Store.kullanim >= Store.KULLANIM_LIMITI_SN -> {
                Store.cezaBaslat()
                "CEZA"
            }
            Store.bakiye <= 0 -> "SURE"
            else -> return
        }
        engelle(sebep)
    }

    private fun engelle(sebep: String) {
        val simdi = SystemClock.elapsedRealtime()
        if (simdi - sonEngelMs < 1500L) return
        sonEngelMs = simdi
        aktifPaket = null

        performGlobalAction(GLOBAL_ACTION_HOME)
        h.postDelayed({
            val i = Intent(this, EngelActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra("sebep", sebep)
            try {
                startActivity(i)
            } catch (_: Exception) {
            }
        }, 300L)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
