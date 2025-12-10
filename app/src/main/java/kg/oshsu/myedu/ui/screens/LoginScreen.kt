package kg.oshsu.myedu.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import kg.oshsu.myedu.MainViewModel
import kg.oshsu.myedu.R
import kg.oshsu.myedu.ui.components.M3ExpressiveShapes
import kg.oshsu.myedu.ui.components.OshSuLogo
import kg.oshsu.myedu.ui.components.PolygonShape

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun LoginScreen(
    vm: MainViewModel,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope
) {
    // Removed local state variables in favor of VM state
    var passwordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Transparent)) {
        val verticalBias by animateFloatAsState(
            targetValue = if (vm.isLoading || vm.isLoginSuccess) 0f else 0.85f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
            label = "VerticalBias"
        )

        val width by animateDpAsState(
            targetValue = if (vm.isLoading || vm.isLoginSuccess) 56.dp else 280.dp,
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
            label = "Width"
        )

        val buttonShape = remember(vm.isLoginSuccess, vm.isLoading) {
            when {
                vm.isLoginSuccess -> PolygonShape(M3ExpressiveShapes.twelveSidedCookie())
                vm.isLoading -> CircleShape
                else -> RoundedCornerShape(100)
            }
        }
        
        val containerColor by animateColorAsState(
            targetValue = if (vm.isLoading && !vm.isLoginSuccess) Color.Transparent else MaterialTheme.colorScheme.primary,
            animationSpec = tween(300),
            label = "ColorFade"
        )

        val rotation by animateFloatAsState(
            targetValue = if (vm.isLoginSuccess) 360f else 0f,
            animationSpec = tween(durationMillis = 2000, easing = LinearEasing),
            label = "CookieRotation"
        )

        val contentAlpha by animateFloatAsState(
            targetValue = if (vm.isLoading || vm.isLoginSuccess) 0f else 1f,
            animationSpec = tween(400)
        )

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp).alpha(contentAlpha).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.height(48.dp))
            OshSuLogo(modifier = Modifier.width(160.dp).height(80.dp))
            Spacer(Modifier.height(32.dp))
            
            Text(stringResource(R.string.welcome_back), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.login_subtitle), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(48.dp))

            Column(verticalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.widthIn(max = 400.dp)) {
                OutlinedTextField(
                    value = vm.loginEmail, 
                    onValueChange = { vm.loginEmail = it }, 
                    label = { Text(stringResource(R.string.email)) }, 
                    leadingIcon = { Icon(Icons.Default.Email, null) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(50), 
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next), 
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }), 
                    singleLine = true
                )
                OutlinedTextField(
                    value = vm.loginPass, 
                    onValueChange = { vm.loginPass = it }, 
                    label = { Text(stringResource(R.string.password)) }, 
                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                    trailingIcon = { IconButton(onClick = { passwordVisible = !passwordVisible }) { Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null) } },
                    modifier = Modifier.fillMaxWidth(), visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(), shape = RoundedCornerShape(50),
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done), 
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); vm.login(vm.loginEmail, vm.loginPass) }), 
                    singleLine = true
                )

                // Added "Remember Me" Row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = vm.rememberMe, 
                        onCheckedChange = { vm.rememberMe = it },
                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.remember_me), 
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (vm.errorMsg != null) {
                Spacer(Modifier.height(24.dp))
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
                    Text(vm.errorMsg!!, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.weight(1f))
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = BiasAlignment(0f, verticalBias)) {
            with(sharedTransitionScope) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .sharedElement(
                            sharedContentState = rememberSharedContentState(key = "cookie_transform"),
                            animatedVisibilityScope = animatedContentScope
                        )
                        .size(width = width, height = 56.dp) 
                        .rotate(rotation)
                        .clip(buttonShape)
                        .background(containerColor)
                        .clickable(enabled = !vm.isLoading && !vm.isLoginSuccess) { vm.login(vm.loginEmail, vm.loginPass) }
                ) {
                    AnimatedContent(
                        targetState = when {
                            vm.isLoginSuccess -> 2
                            vm.isLoading -> 1
                            else -> 0
                        }, 
                        label = "ContentMorph"
                    ) { state ->
                        when(state) {
                            0 -> Text(stringResource(R.string.sign_in), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                            1 -> LoadingIndicator(modifier = Modifier.size(32.dp), color = MaterialTheme.colorScheme.primary) 
                            2 -> Box(Modifier.fillMaxSize()) 
                        }
                    }
                }
            }
        }
    }
}
