package kg.oshsu.myedu.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kg.oshsu.myedu.IdDefinitions
import kg.oshsu.myedu.MainViewModel
import kg.oshsu.myedu.R
import kg.oshsu.myedu.UserData
import kg.oshsu.myedu.StudentInfoResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PersonalInfoScreen(
    vm: MainViewModel,
    onClose: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val currentLang = vm.language
    val context = LocalContext.current
    
    val scope = rememberCoroutineScope()

    // --- LOCAL STATE ---
    var localUser by remember { mutableStateOf<UserData?>(null) }
    var localProfile by remember { mutableStateOf<StudentInfoResponse?>(null) }
    var isFetching by remember { mutableStateOf(true) }
    var isError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val isTransitionComplete = remember { mutableStateOf(false) }
    
    // --- FETCH LOGIC ---
    LaunchedEffect(Unit) {
        delay(500)
        isTransitionComplete.value = true
        
        try {
            val (u, p) = vm.getFreshPersonalInfo()
            localUser = u
            localProfile = p
            isError = false
        } catch (e: Exception) {
            e.printStackTrace()
            isError = true
            errorMessage = e.message ?: "Unknown Error"
        } finally {
            isFetching = false
        }
    }

    val isLoading = (!isTransitionComplete.value || isFetching || !vm.areDictionariesLoaded) && !isError

    // Use local variables
    val user = localUser
    val profile = localProfile
    val pds = profile?.pdsstudentinfo
    val mov = profile?.studentMovement
    val military = profile?.pdsstudentmilitary

    // --- 1. DATA PREPARATION ---
    val apiFullName = listOfNotNull(
        user?.last_name, 
        user?.name, 
        user?.father_name
    ).joinToString(" ").ifBlank { "-" }

    val studentId = user?.id_avn_student?.toString() ?: user?.id?.toString() ?: "-"
    val profileStatus = if (user?.is_pds_approval == true) "Approved" else "Pending"
    
    // --- ANIMATION SETUP ---
    val cookiePolygon = remember { RoundedPolygon.star(12, innerRadius = 0.8f, rounding = CornerRounding(0.2f)) }
    val infiniteTransition = rememberInfiniteTransition(label = "profile_rot")
    val rotation by infiniteTransition.animateFloat(0f, 360f, infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Restart))
    val animatedShape = remember(rotation) { CustomRotatingShape(cookiePolygon, rotation) }

    // --- RESOLVERS ---
    val genderCode = pds?.id_male
    val genderName = if (genderCode != null) {
        IdDefinitions.genders[genderCode]?.getName(currentLang) ?: when (genderCode) {
            1 -> stringResource(R.string.male)
            2 -> stringResource(R.string.female)
            else -> ""
        }
    } else ""
    val genderDisplay = if (genderName.isNotBlank()) "$genderName" else "ID: ${genderCode ?: "-"}"

    val citizenshipId = pds?.id_citizenship
    val citizenshipName = if (citizenshipId != null) {
        val country = IdDefinitions.countries.values.find { it.idIntegrationCitizenship == citizenshipId } 
                      ?: IdDefinitions.countries[citizenshipId]
        country?.getName(currentLang)
    } else null
    val citizenshipDisplay = citizenshipName ?: "ID: ${citizenshipId ?: "-"}"

    val nationalityId = pds?.id_nationality
    val nationalityName = IdDefinitions.nationalities[nationalityId]?.getName(currentLang)
    val nationalityDisplay = nationalityName ?: "ID: ${nationalityId ?: "-"}"

    val specObj = mov?.speciality
    val facObj = mov?.faculty ?: specObj?.faculty
    val eduObj = mov?.edu_form
    val payObj = mov?.payment_form
    val periodObj = mov?.period
    val movTypeObj = mov?.movement_info
    val langObj = mov?.language

    val facultyName = facObj?.getName(currentLang) ?: "-"
    val specialityName = specObj?.getName(currentLang) ?: "-"
    val eduFormName = eduObj?.getName(currentLang) ?: "-"
    val payFormName = payObj?.getName(currentLang) ?: "-"
    val periodName = periodObj?.getName(currentLang) ?: IdDefinitions.getPeriodName(mov?.id_period, currentLang)

    with(sharedTransitionScope) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.personal),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.sharedBounds(
                                sharedContentState = rememberSharedContentState(key = "text_personal"),
                                animatedVisibilityScope = animatedVisibilityScope,
                                resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds()
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.desc_back))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            },
            modifier = Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "personal_card"),
                animatedVisibilityScope = animatedVisibilityScope
            )
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                if (isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoadingIndicator(modifier = Modifier.size(48.dp))
                    }
                } else if (isError) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Failed to load personal info", style = MaterialTheme.typography.titleMedium)
                        if (errorMessage != null) {
                            Text(errorMessage!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = {
                            isFetching = true
                            isError = false
                            scope.launch {
                                try {
                                    val (u, p) = vm.getFreshPersonalInfo()
                                    localUser = u
                                    localProfile = p
                                    isError = false
                                } catch (e: Exception) {
                                    isError = true
                                    errorMessage = e.message
                                } finally {
                                    isFetching = false
                                }
                            }
                        }) {
                            Text("Retry")
                        }
                    }
                } else {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(animationSpec = tween(500))
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            contentPadding = PaddingValues(bottom = 32.dp)
                        ) {
                            // --- HEADER ---
                            item {
                                Spacer(Modifier.height(16.dp))
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(136.dp)) {
                                        Box(modifier = Modifier.fillMaxSize().clip(animatedShape)) {
                                            val apiPhoto = profile?.avatar
                                            key(apiPhoto, vm.avatarRefreshTrigger) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(context)
                                                        .data(apiPhoto)
                                                        .crossfade(true)
                                                        .setParameter("retry_hash", vm.avatarRefreshTrigger)
                                                        .build(),
                                                    contentDescription = null, 
                                                    contentScale = ContentScale.Crop, 
                                                    modifier = Modifier.fillMaxSize()
                                                ) 
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(16.dp))
                                    Text(apiFullName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Text(user?.email ?: "-", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(8.dp))
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text("ID: $studentId") },
                                        icon = { Icon(Icons.Default.Badge, null, Modifier.size(16.dp)) }
                                    )
                                }
                                Spacer(Modifier.height(24.dp))
                            }

                            // --- 1. IDENTITY & BIO ---
                            item {
                                SectionHeader("Identity & Bio", Icons.Default.Face)
                                InfoCard {
                                    DataRow(Icons.Default.Cake, stringResource(R.string.birthday), pds?.birthday)
                                    DataRow(Icons.Default.Face, stringResource(R.string.gender), genderDisplay)
                                    DataRow(Icons.Default.Phone, stringResource(R.string.phone), pds?.phone)
                                    DataRow(Icons.Default.Phone, "Residence Phone", pds?.residence_phone)
                                    DataRow(Icons.Default.Email, "Alt. Email", user?.email2)
                                    DataRow(Icons.Default.Info, "Profile Status", profileStatus)
                                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                    DataRow(Icons.Default.FamilyRestroom, "Marital Status ID", pds?.marital_status?.toString())
                                    DataRow(Icons.Default.CheckCircle, "Is Ethnic?", pds?.is_ethnic?.toString())
                                }
                                Spacer(Modifier.height(24.dp))
                            }

                            // --- 2. ACADEMIC STATUS ---
                            item {
                                SectionHeader("Academic Status", Icons.Outlined.School)
                                InfoCard {
                                    DataRow(Icons.Outlined.Apartment, stringResource(R.string.faculty), facultyName)
                                    DataRow(Icons.Default.Info, "Faculty Short", facObj?.getShortName(currentLang))
                                    if (!facObj?.getInfo(currentLang).isNullOrBlank()) {
                                        DataRow(Icons.Default.Info, "Faculty Info", facObj?.getInfo(currentLang))
                                    }
                                    
                                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

                                    DataRow(Icons.Outlined.Class, stringResource(R.string.speciality), specialityName)
                                    DataRow(Icons.Default.Info, "Spec. Short", specObj?.getShortName(currentLang))
                                    DataRow(Icons.Default.Info, "Spec. Code", specObj?.code ?: mov?.id_speciality?.toString())
                                    if (!specObj?.getInfo(currentLang).isNullOrBlank()) {
                                        DataRow(Icons.Default.Info, "Spec. Info", specObj?.getInfo(currentLang))
                                    }

                                    DataRow(Icons.Outlined.Groups, "Group", mov?.avn_group_name)
                                    DataRow(Icons.Default.DateRange, "Enrollment", mov?.date_movement)
                                    
                                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                    
                                    DataRow(Icons.Default.School, "Education Form", eduFormName)
                                    DataRow(Icons.Default.Info, "Edu Short", eduObj?.getShortName(currentLang))
                                    DataRow(Icons.Default.CheckCircle, "Active Status", eduObj?.status?.toString())
                                    
                                    DataRow(Icons.Outlined.Payments, "Payment", payFormName)
                                    DataRow(Icons.Default.Translate, "Language", mov?.language?.name)
                                    DataRow(Icons.Default.Translate, "Lang. Short", langObj?.short_name)
                                    DataRow(Icons.Default.DateRange, "Current Semester", profile?.active_semester?.toString())
                                    
                                    if (!profile?.active_semesters.isNullOrEmpty()) {
                                        Spacer(Modifier.height(8.dp))
                                        Text("Active Semesters List:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        profile.active_semesters.forEach { sem ->
                                            val sName = sem.getName(currentLang)
                                            val displayName = if (sName.isNotBlank()) "$sName (ID: ${sem.id})" else "ID: ${sem.id}"
                                            DataRow(Icons.Default.DateRange, "Sem ${sem.number_name}", displayName)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(24.dp))
                            }

                            // --- 3. LEGAL & CITIZENSHIP ---
                            item {
                                SectionHeader("Legal & Citizenship", Icons.Outlined.AccountBalance)
                                InfoCard {
                                    DataRow(Icons.Outlined.Flag, "Citizenship", citizenshipDisplay)
                                    DataRow(Icons.Outlined.Flag, "Nationality", nationalityDisplay)
                                    DataRow(Icons.Default.Fingerprint, "PIN", pds?.pin)
                                    DataRow(Icons.Default.Description, "Has Documents", pds?.is_have_document?.toString())
                                    
                                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                    DataRow(Icons.Default.Book, "Passport", pds?.getFullPassport())
                                    DataRow(Icons.Default.Business, "Authority", pds?.release_organ)
                                    DataRow(Icons.Default.DateRange, "Issued", pds?.release_date)
                                    DataRow(Icons.Default.Info, "PDS Info", pds?.info)
                                }
                                Spacer(Modifier.height(24.dp))
                            }

                            // --- 4. FAMILY INFORMATION ---
                            item {
                                SectionHeader("Family Information", Icons.Outlined.FamilyRestroom)
                                InfoCard {
                                    Text("Father", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                    DataRow(Icons.Default.Person, "Name", pds?.father_full_name)
                                    DataRow(Icons.Default.Phone, "Phone", pds?.father_phone)
                                    DataRow(Icons.Default.Info, "Info", pds?.father_info)
                                    
                                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                    
                                    Text("Mother", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                    DataRow(Icons.Default.Person, "Name", pds?.mother_full_name)
                                    DataRow(Icons.Default.Phone, "Phone", pds?.mother_phone)
                                    DataRow(Icons.Default.Info, "Info", pds?.mother_info)
                                }
                                Spacer(Modifier.height(24.dp))
                            }

                            // --- 5. GEOGRAPHY ---
                            item {
                                SectionHeader("Geography", Icons.Outlined.Place)
                                InfoCard {
                                    Text("Main Address", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    DataRow(Icons.Default.Home, "Address", pds?.address)
                                    
                                    val country = IdDefinitions.getCountryName(pds?.id_country, currentLang)
                                    val oblast = IdDefinitions.getOblastName(pds?.id_oblast, currentLang)
                                    val region = IdDefinitions.getRegionName(pds?.id_region, currentLang)
                                    
                                    DataRow(Icons.Default.Public, "Country", "$country (ID: ${pds?.id_country ?: "-"})")
                                    DataRow(Icons.Default.Public, "State", "$oblast (ID: ${pds?.id_oblast ?: "-"})")
                                    DataRow(Icons.Default.Public, "Region", "$region (ID: ${pds?.id_region ?: "-"})")

                                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                    
                                    Text("Birth Place", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    DataRow(Icons.Default.Home, "Birth Addr", pds?.birth_address)
                                    val bCountry = IdDefinitions.getCountryName(pds?.id_birth_country, currentLang)
                                    DataRow(Icons.Default.Public, "B. Country", "$bCountry (ID: ${pds?.id_birth_country ?: "-"})")
                                    DataRow(Icons.Default.Public, "B. State", "ID: ${pds?.id_birth_oblast ?: "-"}")
                                    DataRow(Icons.Default.Public, "B. Region", "ID: ${pds?.id_birth_region ?: "-"}")
                                    
                                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

                                    Text("Residence Place", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    DataRow(Icons.Default.Home, "Res. Addr", pds?.residence_address)
                                    val rCountry = IdDefinitions.getCountryName(pds?.id_residence_country, currentLang)
                                    DataRow(Icons.Default.Public, "R. Country", "$rCountry (ID: ${pds?.id_residence_country ?: "-"})")
                                    DataRow(Icons.Default.Public, "R. State", "ID: ${pds?.id_residence_oblast ?: "-"}")
                                    DataRow(Icons.Default.Public, "R. Region", "ID: ${pds?.id_residence_region ?: "-"}")
                                }
                                Spacer(Modifier.height(24.dp))
                            }

                            // --- 6. MILITARY SERVICE ---
                            item {
                                SectionHeader("Military Service", Icons.Default.Shield)
                                ExpandableCard {
                                    DataRow(Icons.Default.Shield, "Service", military?.name_military)
                                    DataRow(Icons.Default.Badge, "Name", military?.name)
                                    DataRow(Icons.Default.Badge, "Serial", military?.serial_number)
                                    DataRow(Icons.Default.DateRange, "Date", military?.date)
                                    DataRow(Icons.Default.DateRange, "Record Created", military?.created_at)
                                    DataRow(Icons.Default.Settings, "ID", military?.id?.toString())
                                    DataRow(Icons.Default.Settings, "Student ID", military?.id_student?.toString())
                                    DataRow(Icons.Default.Settings, "User ID", military?.id_user?.toString())
                                    DataRow(Icons.Default.DateRange, "Updated", military?.updated_at)
                                }
                                Spacer(Modifier.height(24.dp))
                            }

                            // --- 7. MOVEMENT & FINANCE ---
                            item {
                                SectionHeader("Movement & Finance", Icons.Outlined.History)
                                ExpandableCard {
                                    Text("Movement", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                    DataRow(Icons.Default.ArrowForward, "Type", movTypeObj?.getName(currentLang))
                                    DataRow(Icons.Default.CheckCircle, "Is Student?", movTypeObj?.is_student?.toString())
                                    DataRow(Icons.Default.Description, "Desc", mov?.info_description)
                                    DataRow(Icons.Default.DateRange, "Period", periodName)
                                    DataRow(Icons.Default.DateRange, "Start Date", periodObj?.start)
                                    DataRow(Icons.Default.CheckCircle, "Period Active", periodObj?.active?.toString())
                                    DataRow(Icons.Default.DateRange, "Order", mov?.info)
                                    DataRow(Icons.Default.Settings, "ITNGYRG", mov?.itngyrg?.toString())
                                    DataRow(Icons.Default.Translate, "State Lang Lvl", mov?.id_state_language_level?.toString())
                                    
                                    DataRow(Icons.Default.DateRange, "Mov. Edu Year", mov?.id_edu_year?.toString())
                                    DataRow(Icons.Default.Settings, "Archive User ID", mov?.id_import_archive_user?.toString())
                                    DataRow(Icons.Default.Settings, "Mov. Citizenship", mov?.citizenship?.toString())
                                    DataRow(Icons.Default.Settings, "Mov OO1 ID", mov?.id_oo1?.toString())
                                    DataRow(Icons.Default.Settings, "Mov ZO1 ID", mov?.id_zo1?.toString())
                                    
                                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                    
                                    Text("Library & Finance", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                    val libStatus = if (profile?.is_library_debt == true) "Has Debt" else "Clean"
                                    DataRow(Icons.Default.Book, "Library", libStatus)
                                    DataRow(Icons.Default.Money, "Debt Credits", profile?.access_debt_credit_count?.toString())
                                    
                                    DataRow(Icons.Default.List, "Lib Items", "${profile?.studentlibrary?.size ?: 0}")
                                    DataRow(Icons.Default.List, "Debt Trans", "${profile?.student_debt_transcript?.size ?: 0}")
                                    DataRow(Icons.Default.List, "Total Price", "${profile?.total_price?.size ?: 0}")
                                }
                                Spacer(Modifier.height(24.dp))
                            }

                            // --- 8. SYSTEM METADATA ---
                            item {
                                SectionHeader("System Metadata", Icons.Outlined.Terminal)
                                ExpandableCard {
                                    Text("User & Profile", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    MetaDataRow("User ID", user?.id_user)
                                    MetaDataRow("Uni ID (User)", user?.id_university)
                                    MetaDataRow("Uni ID (Mov)", mov?.id_university)
                                    MetaDataRow("PDS ID", pds?.id)
                                    MetaDataRow("PDS User ID", pds?.id_user)
                                    MetaDataRow("Movement ID", mov?.id_movement ?: mov?.id)
                                    MetaDataRow("Legacy AVN ID", user?.id_avn)
                                    MetaDataRow("Legacy Aryz ID", user?.id_aryz)
                                    
                                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                    
                                    Text("Timestamps", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    MetaDataRow("User Created", user?.created_at)
                                    MetaDataRow("User Updated", user?.updated_at)
                                    MetaDataRow("PDS Created", pds?.created_at)
                                    MetaDataRow("PDS Updated", pds?.updated_at)
                                    MetaDataRow("Mov Created", mov?.created_at)
                                    MetaDataRow("Mov Updated", mov?.updated_at)
                                    
                                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                    
                                    Text("Structure IDs", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    MetaDataRow("Fac Type ID", facObj?.id_faculty_type)
                                    MetaDataRow("Direction ID", specObj?.id_direction)
                                    MetaDataRow("Group Edu Form", eduObj?.id_group_edu_form)
                                    MetaDataRow("OO1 ID", specObj?.id_oo1)
                                    MetaDataRow("ZO1 ID", specObj?.id_zo1)
                                    MetaDataRow("Exam Type ID", pds?.id_exam_type)
                                    MetaDataRow("Round ID", pds?.id_round)
                                    MetaDataRow("Tariff Type ID", mov?.id_tariff_type)
                                    MetaDataRow("AVN Group ID", mov?.id_avn_group)
                                    
                                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                    
                                    Text("Registry Data (Deep)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    MetaDataRow("Faculty ID", facObj?.id)
                                    MetaDataRow("Fac User ID", facObj?.id_user)
                                    MetaDataRow("Fac Uni ID", facObj?.id_university)
                                    MetaDataRow("Fac Created", facObj?.created_at)
                                    MetaDataRow("Spec ID", specObj?.id)
                                    MetaDataRow("Spec User ID", specObj?.id_user)
                                    MetaDataRow("Spec Uni ID", specObj?.id_university)
                                    MetaDataRow("Spec Created", specObj?.created_at)
                                    MetaDataRow("Edu ID", eduObj?.id)
                                    MetaDataRow("Edu User ID", eduObj?.id_user)
                                    MetaDataRow("Edu Uni ID", eduObj?.id_university)
                                    MetaDataRow("Edu Created", eduObj?.created_at)
                                    MetaDataRow("Pay ID", payObj?.id)
                                    MetaDataRow("Pay User ID", payObj?.id_user)
                                    MetaDataRow("Pay Uni ID", payObj?.id_university)
                                    MetaDataRow("Pay Created", payObj?.created_at)
                                    MetaDataRow("Lang ID", langObj?.id)
                                    MetaDataRow("Lang User ID", langObj?.id_user)
                                    MetaDataRow("Lang Uni ID", langObj?.id_university)
                                    MetaDataRow("Citizenship ID", pds?.id_citizenship)
                                    MetaDataRow("Nationality ID", pds?.id_nationality)
                                }
                                Spacer(Modifier.height(32.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- COMPONENTS ---

@Composable
fun SectionHeader(title: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon, 
            contentDescription = null, 
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun InfoCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
fun ExpandableCard(
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "arrow_rot")

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "View Details",
                    style = MaterialTheme.typography.labelLarge, 
                    fontWeight = FontWeight.Medium, 
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    Icons.Default.KeyboardArrowDown, 
                    null, 
                    modifier = Modifier.rotate(rotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    HorizontalDivider(Modifier.padding(bottom = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    content()
                }
            }
        }
    }
}

@Composable
fun DataRow(icon: ImageVector, label: String, value: String?) {
    val displayValue = if (value.isNullOrBlank()) "-" else value
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Text(displayValue, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun MetaDataRow(label: String, value: Any?) {
    val displayValue = value?.toString() ?: "null"
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        Text(displayValue, style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
