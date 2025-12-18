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
                        Text(stringResource(R.string.personal_error_load), style = MaterialTheme.typography.titleMedium)
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
                            Text(stringResource(R.string.personal_retry))
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
                                SectionHeader(stringResource(R.string.personal_identity_bio), Icons.Default.Face)
                                InfoCard {
                                    DataRow(Icons.Default.Cake, stringResource(R.string.birthday), pds?.birthday)
                                    DataRow(Icons.Default.Face, stringResource(R.string.gender), genderDisplay)
                                    DataRow(Icons.Default.Phone, stringResource(R.string.personal_phone), pds?.phone)
                                    DataRow(Icons.Default.Phone, stringResource(R.string.personal_residence_phone), pds?.residence_phone)
                                    DataRow(Icons.Default.Email, stringResource(R.string.personal_alt_email), user?.email2)
                                    DataRow(Icons.Default.Info, stringResource(R.string.personal_profile_status), profileStatus)
                                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                    DataRow(Icons.Default.FamilyRestroom, stringResource(R.string.personal_marital_status), pds?.marital_status?.toString())
                                    DataRow(Icons.Default.CheckCircle, stringResource(R.string.personal_is_ethnic), pds?.is_ethnic?.toString())
                                }
                                Spacer(Modifier.height(24.dp))
                            }

                            // --- 2. ACADEMIC STATUS ---
                            item {
                                SectionHeader(stringResource(R.string.personal_academic_status), Icons.Outlined.School)
                                InfoCard {
                                    DataRow(Icons.Outlined.Apartment, stringResource(R.string.faculty), facultyName)
                                    DataRow(Icons.Default.Info, stringResource(R.string.personal_faculty_short), facObj?.getShortName(currentLang))
                                    if (!facObj?.getInfo(currentLang).isNullOrBlank()) {
                                        DataRow(Icons.Default.Info, stringResource(R.string.personal_faculty_info), facObj?.getInfo(currentLang))
                                    }
                                    
                                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

                                    DataRow(Icons.Outlined.Class, stringResource(R.string.speciality), specialityName)
                                    DataRow(Icons.Default.Info, stringResource(R.string.personal_spec_short), specObj?.getShortName(currentLang))
                                    DataRow(Icons.Default.Info, stringResource(R.string.personal_spec_code), specObj?.code ?: mov?.id_speciality?.toString())
                                    if (!specObj?.getInfo(currentLang).isNullOrBlank()) {
                                        DataRow(Icons.Default.Info, stringResource(R.string.personal_spec_info), specObj?.getInfo(currentLang))
                                    }

                                    DataRow(Icons.Outlined.Groups, stringResource(R.string.personal_group), mov?.avn_group_name)
                                    DataRow(Icons.Default.DateRange, stringResource(R.string.personal_enrollment), mov?.date_movement)
                                    
                                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                    
                                    DataRow(Icons.Default.School, stringResource(R.string.personal_edu_form), eduFormName)
                                    DataRow(Icons.Default.Info, stringResource(R.string.personal_edu_short), eduObj?.getShortName(currentLang))
                                    DataRow(Icons.Default.CheckCircle, stringResource(R.string.personal_active_status), eduObj?.status?.toString())
                                    
                                    DataRow(Icons.Outlined.Payments, stringResource(R.string.personal_payment), payFormName)
                                    DataRow(Icons.Default.Translate, stringResource(R.string.personal_language), mov?.language?.name)
                                    DataRow(Icons.Default.Translate, stringResource(R.string.personal_lang_short), langObj?.short_name)
                                    DataRow(Icons.Default.DateRange, stringResource(R.string.personal_current_semester), profile?.active_semester?.toString())
                                    
                                    if (!profile?.active_semesters.isNullOrEmpty()) {
                                        Spacer(Modifier.height(8.dp))
                                        Text(stringResource(R.string.personal_active_semesters_list), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        profile.active_semesters.forEach { sem ->
                                            val sName = sem.getName(currentLang)
                                            val displayName = if (sName.isNotBlank()) "$sName (ID: ${sem.id})" else "ID: ${sem.id}"
                                            DataRow(Icons.Default.DateRange, "${stringResource(R.string.personal_sem)} ${sem.number_name}", displayName)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(24.dp))
                            }

                            // --- 3. LEGAL & CITIZENSHIP ---
                            item {
                                SectionHeader(stringResource(R.string.personal_legal_citizenship), Icons.Outlined.AccountBalance)
                                InfoCard {
                                    DataRow(Icons.Outlined.Flag, stringResource(R.string.personal_citizenship), citizenshipDisplay)
                                    DataRow(Icons.Outlined.Flag, stringResource(R.string.personal_nationality), nationalityDisplay)
                                    DataRow(Icons.Default.Fingerprint, stringResource(R.string.personal_pin), pds?.pin)
                                    DataRow(Icons.Default.Description, stringResource(R.string.personal_has_documents), pds?.is_have_document?.toString())
                                    
                                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                    DataRow(Icons.Default.Book, stringResource(R.string.personal_passport), pds?.getFullPassport())
                                    DataRow(Icons.Default.Business, stringResource(R.string.personal_authority), pds?.release_organ)
                                    DataRow(Icons.Default.DateRange, stringResource(R.string.personal_issued), pds?.release_date)
                                    DataRow(Icons.Default.Info, stringResource(R.string.personal_pds_info), pds?.info)
                                }
                                Spacer(Modifier.height(24.dp))
                            }

                            // --- 4. FAMILY INFORMATION ---
                            item {
                                SectionHeader(stringResource(R.string.personal_family_info), Icons.Outlined.FamilyRestroom)
                                InfoCard {
                                    Text(stringResource(R.string.personal_father), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                    DataRow(Icons.Default.Person, stringResource(R.string.personal_name), pds?.father_full_name)
                                    DataRow(Icons.Default.Phone, stringResource(R.string.personal_phone), pds?.father_phone)
                                    DataRow(Icons.Default.Info, stringResource(R.string.personal_info), pds?.father_info)
                                    
                                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                    
                                    Text(stringResource(R.string.personal_mother), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                    DataRow(Icons.Default.Person, stringResource(R.string.personal_name), pds?.mother_full_name)
                                    DataRow(Icons.Default.Phone, stringResource(R.string.personal_phone), pds?.mother_phone)
                                    DataRow(Icons.Default.Info, stringResource(R.string.personal_info), pds?.mother_info)
                                }
                                Spacer(Modifier.height(24.dp))
                            }

                            // --- 5. GEOGRAPHY ---
                            item {
                                SectionHeader(stringResource(R.string.personal_geography), Icons.Outlined.Place)
                                InfoCard {
                                    Text(stringResource(R.string.personal_main_address), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    DataRow(Icons.Default.Home, stringResource(R.string.personal_address), pds?.address)
                                    
                                    val country = IdDefinitions.getCountryName(pds?.id_country, currentLang)
                                    val oblast = IdDefinitions.getOblastName(pds?.id_oblast, currentLang)
                                    val region = IdDefinitions.getRegionName(pds?.id_region, currentLang)
                                    
                                    DataRow(Icons.Default.Public, stringResource(R.string.personal_country), "$country (ID: ${pds?.id_country ?: "-"})")
                                    DataRow(Icons.Default.Public, stringResource(R.string.personal_state), "$oblast (ID: ${pds?.id_oblast ?: "-"})")
                                    DataRow(Icons.Default.Public, stringResource(R.string.personal_region), "$region (ID: ${pds?.id_region ?: "-"})")

                                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                    
                                    Text(stringResource(R.string.personal_birth_place), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    DataRow(Icons.Default.Home, stringResource(R.string.personal_birth_addr), pds?.birth_address)
                                    val bCountry = IdDefinitions.getCountryName(pds?.id_birth_country, currentLang)
                                    val bOblast = IdDefinitions.getOblastName(pds?.id_birth_oblast, currentLang)
                                    val bRegion = IdDefinitions.getRegionName(pds?.id_birth_region, currentLang)
                                    DataRow(Icons.Default.Public, stringResource(R.string.personal_b_country), "$bCountry (ID: ${pds?.id_birth_country ?: "-"})")
                                    DataRow(Icons.Default.Public, stringResource(R.string.personal_b_state), "$bOblast (ID: ${pds?.id_birth_oblast ?: "-"})")
                                    DataRow(Icons.Default.Public, stringResource(R.string.personal_b_region), "$bRegion (ID: ${pds?.id_birth_region ?: "-"})")
                                    
                                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

                                    Text(stringResource(R.string.personal_residence_place), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    DataRow(Icons.Default.Home, stringResource(R.string.personal_res_addr), pds?.residence_address)
                                    val rCountry = IdDefinitions.getCountryName(pds?.id_residence_country, currentLang)
                                    val rOblast = IdDefinitions.getOblastName(pds?.id_residence_oblast, currentLang)
                                    val rRegion = IdDefinitions.getRegionName(pds?.id_residence_region, currentLang)
                                    DataRow(Icons.Default.Public, stringResource(R.string.personal_r_country), "$rCountry (ID: ${pds?.id_residence_country ?: "-"})")
                                    DataRow(Icons.Default.Public, stringResource(R.string.personal_r_state), "$rOblast (ID: ${pds?.id_residence_oblast ?: "-"})")
                                    DataRow(Icons.Default.Public, stringResource(R.string.personal_r_region), "$rRegion (ID: ${pds?.id_residence_region ?: "-"})")
                                }
                                Spacer(Modifier.height(24.dp))
                            }

                            // --- 6. MILITARY SERVICE ---
                            item {
                                SectionHeader(stringResource(R.string.personal_military_service), Icons.Default.Shield)
                                ExpandableCard {
                                    DataRow(Icons.Default.Shield, stringResource(R.string.personal_service), military?.name_military)
                                    DataRow(Icons.Default.Badge, stringResource(R.string.personal_name), military?.name)
                                    DataRow(Icons.Default.Badge, stringResource(R.string.personal_serial), military?.serial_number)
                                    DataRow(Icons.Default.DateRange, stringResource(R.string.personal_date), military?.date)
                                    DataRow(Icons.Default.DateRange, stringResource(R.string.personal_record_created), military?.created_at)
                                    DataRow(Icons.Default.Settings, stringResource(R.string.personal_id), military?.id?.toString())
                                    DataRow(Icons.Default.Settings, stringResource(R.string.personal_student_id), military?.id_student?.toString())
                                    DataRow(Icons.Default.Settings, stringResource(R.string.personal_user_id), military?.id_user?.toString())
                                    DataRow(Icons.Default.DateRange, stringResource(R.string.personal_updated), military?.updated_at)
                                }
                                Spacer(Modifier.height(24.dp))
                            }

                            // --- 7. MOVEMENT & FINANCE ---
                            item {
                                SectionHeader(stringResource(R.string.personal_movement_finance), Icons.Outlined.History)
                                ExpandableCard {
                                    Text(stringResource(R.string.personal_movement), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                    DataRow(Icons.Default.ArrowForward, stringResource(R.string.personal_type), movTypeObj?.getName(currentLang))
                                    DataRow(Icons.Default.CheckCircle, stringResource(R.string.personal_is_student), movTypeObj?.is_student?.toString())
                                    DataRow(Icons.Default.Description, stringResource(R.string.personal_desc), mov?.info_description)
                                    DataRow(Icons.Default.DateRange, stringResource(R.string.personal_period), periodName)
                                    DataRow(Icons.Default.DateRange, stringResource(R.string.personal_start_date), periodObj?.start)
                                    DataRow(Icons.Default.CheckCircle, stringResource(R.string.personal_period_active), periodObj?.active?.toString())
                                    DataRow(Icons.Default.DateRange, stringResource(R.string.personal_order), mov?.info)
                                    DataRow(Icons.Default.Settings, stringResource(R.string.personal_itngyrg), mov?.itngyrg?.toString())
                                    DataRow(Icons.Default.Translate, stringResource(R.string.personal_state_lang_lvl), mov?.id_state_language_level?.toString())
                                    
                                    DataRow(Icons.Default.DateRange, stringResource(R.string.personal_mov_edu_year), mov?.id_edu_year?.toString())
                                    DataRow(Icons.Default.Settings, stringResource(R.string.personal_archive_user_id), mov?.id_import_archive_user?.toString())
                                    DataRow(Icons.Default.Settings, stringResource(R.string.personal_mov_citizenship), mov?.citizenship?.toString())
                                    DataRow(Icons.Default.Settings, stringResource(R.string.personal_mov_oo1_id), mov?.id_oo1?.toString())
                                    DataRow(Icons.Default.Settings, stringResource(R.string.personal_mov_zo1_id), mov?.id_zo1?.toString())
                                    
                                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                    
                                    Text(stringResource(R.string.personal_library_finance), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                    val libStatus = if (profile?.is_library_debt == true) "Has Debt" else "Clean"
                                    DataRow(Icons.Default.Book, stringResource(R.string.personal_library), libStatus)
                                    DataRow(Icons.Default.Money, stringResource(R.string.personal_debt_credits), profile?.access_debt_credit_count?.toString())
                                    
                                    DataRow(Icons.Default.List, stringResource(R.string.personal_lib_items), "${profile?.studentlibrary?.size ?: 0}")
                                    DataRow(Icons.Default.List, stringResource(R.string.personal_debt_trans), "${profile?.student_debt_transcript?.size ?: 0}")
                                    DataRow(Icons.Default.List, stringResource(R.string.personal_total_price), "${profile?.total_price?.size ?: 0}")
                                }
                                Spacer(Modifier.height(24.dp))
                            }

                            // --- 8. SYSTEM METADATA ---
                            item {
                                SectionHeader(stringResource(R.string.personal_system_metadata), Icons.Outlined.Terminal)
                                ExpandableCard {
                                    Text(stringResource(R.string.personal_user_profile), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    MetaDataRow(stringResource(R.string.personal_user_id), user?.id_user)
                                    MetaDataRow(stringResource(R.string.personal_uni_id_user), user?.id_university)
                                    MetaDataRow(stringResource(R.string.personal_uni_id_mov), mov?.id_university)
                                    MetaDataRow(stringResource(R.string.personal_pds_id), pds?.id)
                                    MetaDataRow(stringResource(R.string.personal_pds_user_id), pds?.id_user)
                                    MetaDataRow(stringResource(R.string.personal_movement_id), mov?.id_movement ?: mov?.id)
                                    MetaDataRow(stringResource(R.string.personal_legacy_avn_id), user?.id_avn)
                                    MetaDataRow(stringResource(R.string.personal_legacy_aryz_id), user?.id_aryz)
                                    
                                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                    
                                    Text(stringResource(R.string.personal_timestamps), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    MetaDataRow(stringResource(R.string.personal_user_created), user?.created_at)
                                    MetaDataRow(stringResource(R.string.personal_user_updated), user?.updated_at)
                                    MetaDataRow(stringResource(R.string.personal_pds_created), pds?.created_at)
                                    MetaDataRow(stringResource(R.string.personal_pds_updated), pds?.updated_at)
                                    MetaDataRow(stringResource(R.string.personal_mov_created), mov?.created_at)
                                    MetaDataRow(stringResource(R.string.personal_mov_updated), mov?.updated_at)
                                    
                                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                    
                                    Text(stringResource(R.string.personal_structure_ids), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    MetaDataRow(stringResource(R.string.personal_fac_type_id), facObj?.id_faculty_type)
                                    MetaDataRow(stringResource(R.string.personal_direction_id), specObj?.id_direction)
                                    MetaDataRow(stringResource(R.string.personal_group_edu_form), eduObj?.id_group_edu_form)
                                    MetaDataRow(stringResource(R.string.personal_oo1_id), specObj?.id_oo1)
                                    MetaDataRow(stringResource(R.string.personal_zo1_id), specObj?.id_zo1)
                                    MetaDataRow(stringResource(R.string.personal_exam_type_id), pds?.id_exam_type)
                                    MetaDataRow(stringResource(R.string.personal_round_id), pds?.id_round)
                                    MetaDataRow(stringResource(R.string.personal_tariff_type_id), mov?.id_tariff_type)
                                    MetaDataRow(stringResource(R.string.personal_avn_group_id), mov?.id_avn_group)
                                    
                                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                    
                                    Text(stringResource(R.string.personal_registry_data), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    MetaDataRow(stringResource(R.string.personal_faculty_id), facObj?.id)
                                    MetaDataRow(stringResource(R.string.personal_fac_user_id), facObj?.id_user)
                                    MetaDataRow(stringResource(R.string.personal_fac_uni_id), facObj?.id_university)
                                    MetaDataRow(stringResource(R.string.personal_fac_created), facObj?.created_at)
                                    MetaDataRow(stringResource(R.string.personal_spec_id), specObj?.id)
                                    MetaDataRow(stringResource(R.string.personal_spec_user_id), specObj?.id_user)
                                    MetaDataRow(stringResource(R.string.personal_spec_uni_id), specObj?.id_university)
                                    MetaDataRow(stringResource(R.string.personal_spec_created), specObj?.created_at)
                                    MetaDataRow(stringResource(R.string.personal_edu_id), eduObj?.id)
                                    MetaDataRow(stringResource(R.string.personal_edu_user_id), eduObj?.id_user)
                                    MetaDataRow(stringResource(R.string.personal_edu_uni_id), eduObj?.id_university)
                                    MetaDataRow(stringResource(R.string.personal_edu_created), eduObj?.created_at)
                                    MetaDataRow(stringResource(R.string.personal_pay_id), payObj?.id)
                                    MetaDataRow(stringResource(R.string.personal_pay_user_id), payObj?.id_user)
                                    MetaDataRow(stringResource(R.string.personal_pay_uni_id), payObj?.id_university)
                                    MetaDataRow(stringResource(R.string.personal_pay_created), payObj?.created_at)
                                    MetaDataRow(stringResource(R.string.personal_lang_id), langObj?.id)
                                    MetaDataRow(stringResource(R.string.personal_lang_user_id), langObj?.id_user)
                                    MetaDataRow(stringResource(R.string.personal_lang_uni_id), langObj?.id_university)
                                    MetaDataRow(stringResource(R.string.personal_citizenship_id), pds?.id_citizenship)
                                    MetaDataRow(stringResource(R.string.personal_nationality_id), pds?.id_nationality)
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
                    text = stringResource(R.string.personal_view_details),
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
