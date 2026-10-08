package com.prayagi.netraeco

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import kotlin.random.Random
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.prayagi.trikaal.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Ivory=Color(0xFFFAF7F2)
private val Maroon=Color(0xFF5E1724)
private val Saffron=Color(0xFFC85A17)
private val Night=Color(0xFF0A0E2A)
private val Night2=Color(0xFF1B2150)
private val NightHigh=Color(0xFF262D66)
private val Gold=Color(0xFFF2C14E)
private val Cream=Color(0xFFFFF4DC)
/** Static starry night sky (no animation, no network). Stars are fixed by a seed so the sky never changes between runs. */
@Composable private fun StarrySky(modifier:Modifier=Modifier) {
    Canvas(modifier.background(Brush.verticalGradient(listOf(Color(0xFF070A22),Color(0xFF151245),Color(0xFF2A1A52))))) {
        val r=Random(108)
        repeat(220){
            val x=r.nextFloat()*size.width;val y=r.nextFloat()*size.height
            val big=r.nextInt(14)==0
            val rad=(if(big) 2.2f else 0.6f+r.nextFloat()*1.1f)*density
            val a=0.35f+r.nextFloat()*0.65f
            drawCircle(if(r.nextInt(5)==0) Gold.copy(alpha=a) else Color.White.copy(alpha=a),rad,Offset(x,y))
            if(big){val l=rad*3.2f;drawLine(Color.White.copy(alpha=0.55f),Offset(x-l,y),Offset(x+l,y),density*0.8f);drawLine(Color.White.copy(alpha=0.55f),Offset(x,y-l),Offset(x,y+l),density*0.8f)}
        }
        val cx=size.width*0.86f;val cy=size.height*0.07f;val mr=22f*density
        drawCircle(Gold.copy(alpha=0.18f),mr*1.9f,Offset(cx,cy))
        drawCircle(Color(0xFFFFE9A8),mr,Offset(cx,cy))
        drawCircle(Color(0xFF151245),mr*0.88f,Offset(cx+mr*0.45f,cy-mr*0.1f))
    }
}
private fun deg(value:Double)=String.format(Locale.ROOT,"%.4f°",value)

@Composable fun TrikaalCard(initiallyOpen:Boolean = false) {
    var open by remember { mutableStateOf(initiallyOpen) }
    val placeCtx = androidx.compose.ui.platform.LocalContext.current
    var place by remember { mutableStateOf(TrikaalPlaces.load(placeCtx)) }
    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Night2,contentColor=Cream)) { Column(Modifier.padding(16.dp)) {
        Text("✦ NETRA TRIKAAL ✦",style=MaterialTheme.typography.titleLarge,color=Gold)
        Text("Vedic chart calculations • Lahiri • local profiles")
        OutlinedButton(onClick={open=true},colors=ButtonDefaults.outlinedButtonColors(contentColor=Gold),border=androidx.compose.foundation.BorderStroke(1.dp,Gold)){Text("Open Trikaal / त्रिकाल खोलें")}
    } }
    if(open) androidx.compose.ui.window.Dialog(onDismissRequest={open=false},properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)) {
        MaterialTheme(colorScheme=darkColorScheme(primary=Gold,onPrimary=Night,secondary=Color(0xFFFFB74D),background=Night,surface=Night,onSurface=Cream,onBackground=Cream,surfaceVariant=Night2,onSurfaceVariant=Cream,surfaceContainerHigh=NightHigh,surfaceContainerHighest=NightHigh,surfaceContainer=Night2,outline=Gold,outlineVariant=Color(0xFF5A5F99))) {
            Surface(Modifier.fillMaxSize(),color=Night) {
                StarrySky(Modifier.fillMaxSize())
                Column(Modifier.safeDrawingPadding().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    TrikaalTicker(place)
                    var hasKundli by remember { mutableStateOf(false) }
                    androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text("✦ NETRA TRIKAAL ✦",style=MaterialTheme.typography.titleLarge,color=Gold);TextButton(onClick={open=false}){Text("Close / बंद")}}
                        TrikaalLocationChoice(place) { place = it }
                        TrikaalContent { hasKundli = it }
                        TrikaalSoon()
                        if(hasKundli) androidx.compose.foundation.layout.Spacer(Modifier.height(80.dp))
                    }
                    if(hasKundli) androidx.compose.foundation.layout.Box(Modifier.align(androidx.compose.ui.Alignment.BottomEnd).padding(4.dp)) { KundliPdfSoon() }
                    }
                }
            }
        }
    }
}

@Composable private fun TrikaalContent(onKundli: (Boolean) -> Unit = {}) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var profile by remember { mutableStateOf(TrikaalProfile("","","","","","","","")) }
    var profiles by remember { mutableStateOf(TrikaalProfiles.read(context)) }
    var chart by remember { mutableStateOf<Chart?>(null) };var status by remember { mutableStateOf("") }
    LaunchedEffect(chart != null) { onKundli(chart != null) }
    var busy by remember { mutableStateOf(false) };var view by remember { mutableStateOf("Quick") }
    var layout by remember { mutableStateOf("North") }
    var defaultKey by remember { mutableStateOf(TrikaalProfiles.defaultKey(context)) }
    var transits by remember { mutableStateOf<Computation<List<Transit>>?>(null) }
    var asOf by remember { mutableStateOf<Instant?>(null) }
    Text("Birth details / जन्म विवरण",style=MaterialTheme.typography.titleMedium)
    Text("Name, date, time and place are all you need to fill in. Nothing is guessed or sent to a server.",style=MaterialTheme.typography.bodySmall)
    @Composable fun field(label:String,value:String,change:(String)->Unit) { OutlinedTextField(value=value,onValueChange=change,label={Text(label)},singleLine=true,modifier=Modifier.fillMaxWidth()) }
    TrikaalBirthForm(profile){profile=it}
    suspend fun calculate(snapshot:TrikaalProfile) {
        busy=true;chart=null;transits=null;status="Calculating on device..."
        val result=withContext(Dispatchers.Default) {
            try {
                val b=BirthInput(snapshot.name,LocalDate.parse(snapshot.date),snapshot.time.takeIf{it.isNotBlank()}?.let(LocalTime::parse),snapshot.zone.takeIf{it.isNotBlank()},snapshot.latitude.toDoubleOrNull(),snapshot.longitude.toDoubleOrNull(),snapshot.place,snapshot.offset.takeIf{it.isNotBlank()}?.let(ZoneOffset::of))
                AstroCore.computeChart(b)
            } catch(e:Exception) { Computation.Unavailable("Invalid date, time or offset. Use the shown formats.") }
        }
        when(result) {
            is Computation.Available -> { chart=result.value;status="Calculated. Nothing uploaded."; val now=Instant.now();asOf=now;transits=withContext(Dispatchers.Default){AstroCore.gochar(now,result.value)} }
            is Computation.Unavailable -> status="Unavailable: ${result.reason}"
        }
        busy=false
    }
    LaunchedEffect(Unit) { TrikaalProfiles.defaultProfile(context)?.let { profile=it; calculate(it) } }
    Button(enabled=!busy,onClick={ scope.launch { calculate(profile) } }){Text(if(busy)"Calculating..." else "Calculate / गणना")}
    Text(status)
    OutlinedButton(enabled=!busy,onClick={scope.launch{val p=profile;val ok=withContext(Dispatchers.IO){TrikaalProfiles.save(context,p)};profiles=TrikaalProfiles.read(context);status=if(ok)"Saved on this device only." else "Unavailable: profile not saved (up to ${TrikaalProfiles.MAX_PROFILES} profiles; delete one first)."}}){Text("Save locally / सहेजें")}
    profiles.forEach { p -> Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        TextButton(onClick={profile=p;scope.launch{calculate(p)}}){Text((if(TrikaalProfiles.key(p)==defaultKey)"★ " else "")+"${p.name.ifBlank{"Profile"}} · ${p.date}")}
        Row {
            TextButton(onClick={scope.launch{withContext(Dispatchers.IO){TrikaalProfiles.setDefault(context,p)};defaultKey=TrikaalProfiles.defaultKey(context)}}){Text(if(TrikaalProfiles.key(p)==defaultKey)"Default" else "Make default")}
            TextButton(onClick={scope.launch{withContext(Dispatchers.IO){TrikaalProfiles.delete(context,p)};profiles=TrikaalProfiles.read(context);defaultKey=TrikaalProfiles.defaultKey(context)}}){Text("Delete")}
        }
    } }
    chart?.let { c ->
        HorizontalDivider()
        Row { listOf("Quick","Detailed","Technical").forEach{v->TextButton(onClick={view=v}){Text(if(v==view)"• $v" else v)}} }
        Text("Kundli / कुंडली",style=MaterialTheme.typography.titleLarge)
        if(c.input.placeLabel?.isNotBlank()==true) Text("Place at birth / जन्म स्थान: ${c.input.placeLabel}",style=MaterialTheme.typography.bodyMedium)
        Text("Lagna / लग्न: ${SIGN_NAMES[c.lagnaRashi]} ${deg(c.lagna%30)}")
        Text("Moon / चंद्र: ${SIGN_NAMES[c.positions.single{it.graha==Graha.MOON}.rashi]}")
        if(view!="Quick") {
            Row { listOf("North","South").forEach{v->TextButton(onClick={layout=v}){Text("$v chart")}} }
            KundliDrawing(c,layout)
        }
        c.positions.forEach { p ->
            Text("${p.graha.label}: ${SIGN_NAMES[p.rashi]} ${deg(p.degreeInSign)}${if(p.retrograde)" R" else ""}")
            if(view!="Quick") Text("${NAKSHATRA_NAMES[p.nakshatra]} · pada ${p.pada} · house ${c.houseOf(p)}",style=MaterialTheme.typography.bodySmall)
        }
        if(view=="Technical") {
            Text("UTC birth: ${c.born}\nEngine: Swiss Ephemeris Java ${c.engineVersion}, Moshier\nAyanamsa: Lahiri. Mean Rahu; Ketu = Rahu + 180°. Whole-sign houses. Apparent geocentric positions. Dasha year = 365.25 days. Runtime IANA historical timezone rules.")
            c.houseLords.forEachIndexed{i,l->Text("House ${i+1} lord: ${l.label}")}
        }
        Text("Vimshottari / विंशोत्तरी दशा",style=MaterialTheme.typography.titleMedium)
        val periods=remember(c){vimshottariDasha(c)};val zone=ZoneId.of(c.input.tzId)
        val format=DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm xxx").withZone(zone)
        val now=asOf ?: Instant.now();val current=currentPeriod(periods,now)
        Text(current?.let{"Current: ${it.first.lord.label} / ${it.second?.lord?.label ?: "Unavailable"}"} ?: "Unavailable: now is outside this 120-year cycle")
        periods.forEach { p ->
            Text("${p.lord.label}: ${format.format(p.start)} to ${format.format(p.end)}")
            if(view!="Quick" && (view=="Technical" || p.contains(now))) p.children.forEach{a->Text("  ${a.lord.label}: ${format.format(a.start)} to ${format.format(a.end)}",style=MaterialTheme.typography.bodySmall)}
        }
        Text("Gochar / गोचर",style=MaterialTheme.typography.titleMedium)
        Text("Calculated as of ${asOf ?: "Unavailable"}; refresh for current positions.",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(enabled=!busy,onClick={scope.launch{busy=true;val n=Instant.now();transits=withContext(Dispatchers.Default){AstroCore.gochar(n,c)};asOf=n;busy=false}}){Text("Refresh Gochar")}
        when(val t=transits) {
            is Computation.Available -> t.value.forEach{Text("${it.position.graha.label}: ${SIGN_NAMES[it.position.rashi]} ${deg(it.position.degreeInSign)} · natal Lagna house ${it.houseFromLagna}, Moon house ${it.houseFromMoon}")}
            is Computation.Unavailable -> Text("Unavailable: ${t.reason}")
            null -> Text("Unavailable: transits not calculated")
        }
        Text("Daily rashifal / आज का राशिफल: ${c.input.name.ifBlank{"this profile"}}"+(if(TrikaalProfiles.key(profile)==defaultKey)" (default profile)" else ""),style=MaterialTheme.typography.titleMedium)
        when(val tr=transits) {
            is Computation.Available -> when(val r=Rashifal.daily(c,tr.value,periods,now)) {
                is Computation.Available -> r.value.forEach { l -> Text(l.title,style=MaterialTheme.typography.titleSmall);Text(l.text);Text("Rule: ${l.rule}",style=MaterialTheme.typography.bodySmall) }
                is Computation.Unavailable -> Text("Unavailable: ${r.reason}")
            }
            else -> Text("Unavailable: refresh Gochar to calculate today's rashifal")
        }
    }
    HorizontalDivider()
    Text("Readings follow Vedic principles, not scientific certainty. Not medical, legal or financial advice. / यह वैदिक गणना है, वैज्ञानिक निश्चितता नहीं।",style=MaterialTheme.typography.bodySmall)
    TextButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/prayagi-store-and-services/netra-eco-app")))}){Text("Source code / AGPL-3.0 / स्रोत")}
    Text("Feedback: use Eco's reporting section. Birth details and saved profiles are never automatically attached.",style=MaterialTheme.typography.bodySmall)
}

/** North chart: fixed houses; South chart: fixed signs. Labels carry occupants and sign/house. */
@Composable private fun KundliDrawing(c:Chart,layout:String) {
    Canvas(Modifier.fillMaxWidth().aspectRatio(1f).background(Ivory).padding(8.dp)) {
        val w=size.width;val h=size.height;val stroke=2.dp.toPx()
        val paint=android.graphics.Paint().apply{color=android.graphics.Color.rgb(94,23,36);textSize=11.dp.toPx();textAlign=android.graphics.Paint.Align.CENTER;isAntiAlias=true}
        fun line(x1:Float,y1:Float,x2:Float,y2:Float)=drawLine(Maroon,Offset(x1*w,y1*h),Offset(x2*w,y2*h),stroke)
        fun label(x:Float,y:Float,title:String,ps:List<Position>){
            drawContext.canvas.nativeCanvas.drawText(title,x*w,y*h,paint)
            ps.chunked(3).take(3).forEachIndexed{i,p->drawContext.canvas.nativeCanvas.drawText(p.joinToString(" "){it.graha.name.take(2)},x*w,y*h+(i+1)*14.dp.toPx(),paint)}
        }
        line(0f,0f,1f,0f);line(1f,0f,1f,1f);line(1f,1f,0f,1f);line(0f,1f,0f,0f)
        if(layout=="South") {
            for(i in 1..3){val f=i/4f;line(f,0f,f,0.25f);line(f,0.75f,f,1f);line(0f,f,0.25f,f);line(0.75f,f,1f,f)}
            line(0.25f,0.25f,0.75f,0.25f);line(0.25f,0.75f,0.75f,0.75f);line(0.25f,0.25f,0.25f,0.75f);line(0.75f,0.25f,0.75f,0.75f)
            val cells=listOf(1 to 0,2 to 0,3 to 0,3 to 1,3 to 2,3 to 3,2 to 3,1 to 3,0 to 3,0 to 2,0 to 1,0 to 0)
            cells.forEachIndexed{sign,(x,y)->label((x+.5f)/4,(y+.25f)/4,"${sign+1}${if(sign==c.lagnaRashi)" Asc" else ""}",c.positions.filter{it.rashi==sign})}
        } else {
            line(0f,0f,1f,1f);line(1f,0f,0f,1f);line(.5f,0f,1f,.5f);line(1f,.5f,.5f,1f);line(.5f,1f,0f,.5f);line(0f,.5f,.5f,0f)
            val centers=listOf(.5f to .18f,.25f to .06f,.08f to .24f,.25f to .43f,.08f to .67f,.25f to .87f,.5f to .68f,.75f to .87f,.92f to .67f,.75f to .43f,.92f to .24f,.75f to .06f)
            centers.forEachIndexed{i,(x,y)->label(x,y,"H${i+1} S${(c.lagnaRashi+i)%12+1}",c.positions.filter{c.houseOf(it)==i+1})}
        }
    }
}
