package kg.oshsu.myedu.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Money
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings // <--- Missing import added
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kg.oshsu.myedu.MainViewModel
import kg.oshsu.myedu.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun PersonalInfoScreen(
    vm: MainViewModel,
    onClose: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val user = vm.userData
    val profile = vm.profileData
    val pds = profile?.pdsstudentinfo
    val mov = profile?.studentMovement
    val military = profile?.pdsstudentmilitary
    
    // --- 1. BASIC IDENTIFICATION ---
    val studentId = user?.id_avn_student?.toString() ?: user?.id?.toString() ?: "-"
    val email = user?.email ?: "-"
    val email2 = user?.email2 
    
    val baseName = vm.uiName
    val fullName = if (!user?.father_name.isNullOrBlank() && !baseName.contains(user!!.father_name!!)) {
        "$baseName ${user.father_name}"
    } else {
        baseName
    }

    val phone = pds?.phone ?: "-"
    val birthday = pds?.birthday ?: "-"
    
    val genderCode = pds?.id_male
    val gender = when (genderCode) {
        1 -> stringResource(R.string.male)
        2 -> stringResource(R.string.female)
        else -> "Code: $genderCode"
    }

    // --- 2. PASSPORT & LEGAL ---
    val passport = pds?.getFullPassport() ?: "-"
    val pin = pds?.pin ?: "-"
    val authority = pds?.release_organ ?: "-"
    val dateIssue = pds?.release_date ?: "-"
    
    val address = pds?.address ?: "-"
    val birthAddress = pds?.birth_address
    val residenceAddress = pds?.residence_address
    val citizenshipId = pds?.id_citizenship?.toString() ?: "-"
    val nationalityId = pds?.id_nationality?.toString() ?: "-"

    // --- 3. ACADEMIC & MOVEMENT ---
    val enrollDate = mov?.date_movement ?: "-"
    val enrollOrder = mov?.info ?: "-"
    val groupName = mov?.avn_group_name ?: "-"
    val specialityCode = mov?.speciality?.code ?: "-"
    
    val moveType = mov?.movement_info?.get() ?: "-"
    val eduForm = mov?.edu_form?.get() ?: "-"
    val paymentType = mov?.payment_form?.get() ?: "-"
    val language = mov?.language?.name ?: "-"

    // --- 4. LIBRARY & DEBT ---
    val debtCredits = profile?.access_debt_credit_count?.toString() ?: "0.0"
    val libDebt = if (profile?.is_library_debt == true) "Yes" else "No"

    // --- 5. SYSTEM INFO ---
    val created = user?.created_at ?: "-"
    val updated = user?.updated_at ?: "-"
    val status = if (user?.is_pds_approval == true) "Approved" else "Pending"
    val pdsId = pds?.id?.toString() ?: "-"
    val uniId = user?.id_university?.toString() ?: "-"
    val userId = user?.id_user?.toString() ?: "-"

    with(sharedTransitionScope) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                TopAppBar(
                    title = { },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.desc_back))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            modifier = Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "personal_card"),
                animatedVisibilityScope = animatedVisibilityScope
            )
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                
                Text(
                    stringResource(R.string.personal),
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .align(Alignment.Start)
                        .sharedBounds(
                            sharedContentState = rememberSharedContentState(key = "text_personal"),
                            animatedVisibilityScope = animatedVisibilityScope,
                            resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds()
                        )
                )

                Spacer(Modifier.height(32.dp))

                // --- 1. PERSONAL PROFILE ---
                InfoItem(Icons.Default.Person, stringResource(R.string.full_name), fullName)
                InfoItem(Icons.Default.Badge, stringResource(R.string.student_id), studentId)
                InfoItem(Icons.Default.Email, stringResource(R.string.email), email)
                if (!email2.isNullOrBlank()) InfoItem(Icons.Default.Email, "Alt. Email", email2)
                InfoItem(Icons.Default.Phone, stringResource(R.string.phone), phone)
                InfoItem(Icons.Default.Cake, stringResource(R.string.birthday), birthday)
                InfoItem(Icons.Default.Face, stringResource(R.string.gender), gender)

                Spacer(Modifier.height(24.dp))
                SectionHeader("Legal & Passport")
                
                InfoItem(Icons.Default.Fingerprint, "PIN", pin)
                InfoItem(Icons.Default.AccountBalance, stringResource(R.string.passport), passport)
                InfoItem(Icons.Default.AccountBalance, "Authority", authority)
                InfoItem(Icons.Default.DateRange, "Date of Issue", dateIssue)
                InfoItem(Icons.Default.Flag, "Citizenship ID", citizenshipId)
                InfoItem(Icons.Default.Flag, "Nationality ID", nationalityId)
                InfoItem(Icons.Default.Home, "Main Address", address)
                if (!birthAddress.isNullOrBlank()) InfoItem(Icons.Default.Home, "Birth Address", birthAddress)
                if (!residenceAddress.isNullOrBlank()) InfoItem(Icons.Default.Home, "Residence", residenceAddress)

                Spacer(Modifier.height(24.dp))
                SectionHeader("Enrollment & Academic")

                InfoItem(Icons.Default.DateRange, "Enrollment Date", enrollDate)
                InfoItem(Icons.Default.Info, "Movement Type", moveType)
                InfoItem(Icons.Default.School, "Order Number", enrollOrder)
                InfoItem(Icons.Default.School, "Group", groupName)
                InfoItem(Icons.Default.School, "Education Form", eduForm)
                InfoItem(Icons.Default.Money, "Payment Form", paymentType)
                InfoItem(Icons.Default.Language, "Language", language)
                InfoItem(Icons.Default.School, "Spec. Code", specialityCode)
                
                // --- LIBRARY DEBT ---
                InfoItem(Icons.Default.Book, "Library Debt", libDebt)
                InfoItem(Icons.Default.Info, "Debt Credits", debtCredits)

                // --- FAMILY INFO ---
                if (!pds?.father_full_name.isNullOrBlank() || !pds?.mother_full_name.isNullOrBlank()) {
                    Spacer(Modifier.height(24.dp))
                    SectionHeader("Family Information")
                    pds?.father_full_name?.takeIf { it.isNotBlank() }?.let { InfoItem(Icons.Default.FamilyRestroom, "Father", it) }
                    pds?.father_phone?.takeIf { it.isNotBlank() }?.let { InfoItem(Icons.Default.Phone, "Father's Phone", it) }
                    pds?.father_info?.takeIf { it.isNotBlank() }?.let { InfoItem(Icons.Default.Info, "Father's Info", it) }
                    
                    pds?.mother_full_name?.takeIf { it.isNotBlank() }?.let { InfoItem(Icons.Default.FamilyRestroom, "Mother", it) }
                    pds?.mother_phone?.takeIf { it.isNotBlank() }?.let { InfoItem(Icons.Default.Phone, "Mother's Phone", it) }
                    pds?.mother_info?.takeIf { it.isNotBlank() }?.let { InfoItem(Icons.Default.Info, "Mother's Info", it) }
                }

                // --- MILITARY ---
                if (military != null && (!military.name_military.isNullOrBlank() || !military.serial_number.isNullOrBlank())) {
                    Spacer(Modifier.height(24.dp))
                    SectionHeader("Military Service")
                    military.name_military?.let { InfoItem(Icons.Default.Shield, "Service", it) }
                    military.serial_number?.let { InfoItem(Icons.Default.Shield, "ID/Serial", it) }
                    military.date?.let { InfoItem(Icons.Default.DateRange, "Date", it) }
                }

                // --- SYSTEM METADATA ---
                Spacer(Modifier.height(24.dp))
                SectionHeader("System Metadata")
                InfoItem(Icons.Default.Info, "Profile Status", status)
                InfoItem(Icons.Default.DateRange, "Created At", created)
                InfoItem(Icons.Default.DateRange, "Updated At", updated)
                InfoItem(Icons.Default.Settings, "User ID", userId)
                InfoItem(Icons.Default.Settings, "PDS ID", pdsId)
                InfoItem(Icons.Default.Settings, "Uni ID", uniId)
                InfoItem(Icons.Default.Settings, "Archive User ID", mov?.id_import_archive_user?.toString() ?: "-")

                // --- FLAGS & RAW IDs (The "Deep Dive") ---
                Spacer(Modifier.height(24.dp))
                SectionHeader("Technical Flags & IDs")
                
                InfoItem(Icons.Default.Lock, "Check Code", user?.check?.toString() ?: "null")
                InfoItem(Icons.Default.Lock, "Is Working", user?.is_working?.toString() ?: "null")
                InfoItem(Icons.Default.Lock, "Is Student", user?.is_student?.toString() ?: "null")
                InfoItem(Icons.Default.Lock, "Reset Password", user?.is_reset_password?.toString() ?: "null")
                InfoItem(Icons.Default.Flag, "Ethnic", pds?.is_ethnic?.toString() ?: "null")
                InfoItem(Icons.Default.Flag, "Have Document", pds?.is_have_document?.toString() ?: "null")
                
                InfoItem(Icons.Default.Settings, "ID Period", mov?.id_period?.toString() ?: "null")
                InfoItem(Icons.Default.Settings, "ID Tariff", mov?.id_tariff_type?.toString() ?: "null")
                InfoItem(Icons.Default.Settings, "ITNGYRG", mov?.itngyrg?.toString() ?: "null")
                InfoItem(Icons.Default.Settings, "ID Payment Form", mov?.id_payment_form?.toString() ?: "null")
                InfoItem(Icons.Default.Settings, "ID AVN", user?.id_avn?.toString() ?: "null")
                InfoItem(Icons.Default.Settings, "ID Aryz", user?.id_aryz?.toString() ?: "null")
                
                // Geo IDs
                InfoItem(Icons.Default.Home, "ID Country", pds?.id_country?.toString() ?: "null")
                InfoItem(Icons.Default.Home, "ID Region", pds?.id_region?.toString() ?: "null")
                InfoItem(Icons.Default.Home, "ID Oblast", pds?.id_oblast?.toString() ?: "null")
                
                Spacer(Modifier.height(48.dp))
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun InfoItem(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}
