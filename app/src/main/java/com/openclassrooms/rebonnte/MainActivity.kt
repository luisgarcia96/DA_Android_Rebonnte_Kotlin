package com.openclassrooms.rebonnte

import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.openclassrooms.rebonnte.ui.aisle.AisleScreen
import com.openclassrooms.rebonnte.ui.aisle.AisleViewModel
import com.openclassrooms.rebonnte.ui.auth.AuthScreen
import com.openclassrooms.rebonnte.ui.auth.AuthViewModel
import com.openclassrooms.rebonnte.ui.medicine.MedicineScreen
import com.openclassrooms.rebonnte.ui.medicine.MedicineDetailActivity
import com.openclassrooms.rebonnte.ui.medicine.MedicineViewModel
import com.openclassrooms.rebonnte.ui.theme.RebonnteTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull

class MainActivity : ComponentActivity() {
    private val medicineViewModel: MedicineViewModel by viewModels()
    private val aisleViewModel: AisleViewModel by viewModels()
    private val authViewModel: AuthViewModel by viewModels()
    private var hasResumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApp(medicineViewModel, aisleViewModel, authViewModel)
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasResumed) {
            medicineViewModel.reload(force = true)
            aisleViewModel.reload(force = true)
        }
        hasResumed = true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyApp(
    medicineViewModel: MedicineViewModel,
    aisleViewModel: AisleViewModel,
    authViewModel: AuthViewModel
) {
    val authState by authViewModel.uiState.collectAsState()

    LaunchedEffect(authState.isAuthenticated) {
        if (authState.isAuthenticated) {
            medicineViewModel.reload()
            aisleViewModel.reload()
        }
    }

    RebonnteTheme {
        if (authState.isAuthenticated) {
            StockApp(
                medicineViewModel = medicineViewModel,
                aisleViewModel = aisleViewModel,
                userEmail = authState.userEmail,
                onSignOut = authViewModel::signOut
            )
        } else {
            AuthScreen(
                errorMessage = authState.errorMessage,
                isSubmitting = authState.isSubmitting,
                onSignIn = authViewModel::signIn,
                onCreateAccount = authViewModel::createAccount,
                onErrorShown = authViewModel::clearError
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StockApp(
    medicineViewModel: MedicineViewModel,
    aisleViewModel: AisleViewModel,
    userEmail: String?,
    onSignOut: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val route = navBackStackEntry?.destination?.route
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(medicineViewModel) {
        medicineViewModel.errorMessage
            .filterNotNull()
            .collectLatest { message ->
                snackbarHostState.showSnackbar(message)
                medicineViewModel.clearError()
            }
    }

    LaunchedEffect(aisleViewModel) {
        aisleViewModel.errorMessage
            .filterNotNull()
            .collectLatest { message ->
                snackbarHostState.showSnackbar(message)
                aisleViewModel.clearError()
            }
    }

    Scaffold(
        topBar = {
            MainTopBar(route, medicineViewModel, aisleViewModel, userEmail, onSignOut)
        },
        bottomBar = { MainBottomBar(route, navController) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            MainFloatingActionButton(route, aisleViewModel)
        }
    ) {
        NavHost(
            modifier = Modifier.padding(it),
            navController = navController,
            startDestination = "aisle",
            enterTransition = { fadeIn(animationSpec = tween(90)) },
            exitTransition = { fadeOut(animationSpec = tween(60)) },
            popEnterTransition = { fadeIn(animationSpec = tween(90)) },
            popExitTransition = { fadeOut(animationSpec = tween(60)) }
        ) {
            composable("aisle") { AisleScreen(aisleViewModel) }
            composable("medicine") {
                MedicineScreen(
                    medicineViewModel,
                    onAddTestData = {
                        aisleViewModel.addTestAisles(5) { aisleNames ->
                            medicineViewModel.addTestMedicines(aisleNames)
                        }
                    },
                    onClearAllData = {
                        medicineViewModel.clearAllMedicines()
                        aisleViewModel.clearAllAisles()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopBar(
    route: String?,
    medicineViewModel: MedicineViewModel,
    aisleViewModel: AisleViewModel,
    userEmail: String?,
    onSignOut: () -> Unit
) {
    var isMedicineSearchActive by rememberSaveable { mutableStateOf(false) }
    var medicineSearchQuery by rememberSaveable { mutableStateOf("") }
    var isAisleSearchActive by rememberSaveable { mutableStateOf(false) }
    var aisleSearchQuery by rememberSaveable { mutableStateOf("") }
    var isSortMenuExpanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy((-1).dp)) {
        TopAppBar(
            title = {
                Column {
                    Text(text = if (route == "aisle") "Aisle" else "Medicines")
                    if (userEmail != null) {
                        Text(
                            text = "Connecte : $userEmail",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            },
            actions = {
                if (route == "medicine") {
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        IconButton(onClick = { isSortMenuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Sort medicines"
                            )
                        }
                        DropdownMenu(
                            expanded = isSortMenuExpanded,
                            onDismissRequest = { isSortMenuExpanded = false },
                            offset = DpOffset(x = 0.dp, y = 0.dp)
                        ) {
                            DropdownMenuItem(
                                onClick = {
                                    medicineViewModel.sortByNone()
                                    isSortMenuExpanded = false
                                },
                                text = { Text("Sort by None") }
                            )
                            DropdownMenuItem(
                                onClick = {
                                    medicineViewModel.sortByName()
                                    isSortMenuExpanded = false
                                },
                                text = { Text("Sort by Name") }
                            )
                            DropdownMenuItem(
                                onClick = {
                                    medicineViewModel.sortByStock()
                                    isSortMenuExpanded = false
                                },
                                text = { Text("Sort by Stock") }
                            )
                        }
                    }
                }
                IconButton(onClick = onSignOut) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Se deconnecter"
                    )
                }
            }
        )
        when (route) {
            "aisle" -> EmbeddedSearchBar(
                query = aisleSearchQuery,
                onQueryChange = {
                    aisleViewModel.filterByName(it)
                    aisleSearchQuery = it
                },
                isSearchActive = isAisleSearchActive,
                onActiveChanged = { isAisleSearchActive = it },
                searchContentDescription = "Search aisles"
            )
            "medicine" -> EmbeddedSearchBar(
                query = medicineSearchQuery,
                onQueryChange = {
                    medicineViewModel.filterByName(it)
                    medicineSearchQuery = it
                },
                isSearchActive = isMedicineSearchActive,
                onActiveChanged = { isMedicineSearchActive = it },
                searchContentDescription = "Search medicines"
            )
        }
    }
}

@Composable
private fun MainBottomBar(
    route: String?,
    navController: NavController
) {
    NavigationBar {
        NavigationBarItem(
            icon = { Icon(Icons.Default.Home, contentDescription = null) },
            label = { Text("Aisle") },
            selected = route == "aisle",
            onClick = { navController.navigateToTopLevelDestination("aisle") }
        )
        NavigationBarItem(
            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
            label = { Text("Medicine") },
            selected = route == "medicine",
            onClick = { navController.navigateToTopLevelDestination("medicine") }
        )
    }
}

private fun NavController.navigateToTopLevelDestination(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun MainFloatingActionButton(
    route: String?,
    aisleViewModel: AisleViewModel
) {
    val context = LocalContext.current

    val contentDescription = if (route == "medicine") "Add medicine" else "Add aisle"

    FloatingActionButton(onClick = {
        when (route) {
            "medicine" -> context.startActivity(
                Intent(context, MedicineDetailActivity::class.java)
                    .putExtra("isNewMedicine", true)
                    .putStringArrayListExtra(
                        "aisleNames",
                        ArrayList(aisleViewModel.aisles.value.map { it.name })
                    )
            )
            "aisle" -> aisleViewModel.addRandomAisle()
        }
    }) {
        Icon(imageVector = Icons.Default.Add, contentDescription = contentDescription)
    }
}

@Composable
fun currentRoute(navController: NavController): String? {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    return navBackStackEntry?.destination?.route
}

@Composable
fun EmbeddedSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    isSearchActive: Boolean,
    onActiveChanged: (Boolean) -> Unit,
    searchContentDescription: String,
    modifier: Modifier = Modifier,
) {
    val activeChanged: (Boolean) -> Unit = { active ->
        onQueryChange("")
        onActiveChanged(active)
    }

    val shape: Shape = RoundedCornerShape(16.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 16.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSearchActive) {
            IconButton(onClick = { activeChanged(false) }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Close search",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .semantics { contentDescription = searchContentDescription },
            singleLine = true,
            decorationBox = { innerTextField ->
                if (query.isEmpty()) {
                    Text(
                        text = "Search",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                innerTextField()
            }
        )

        if (isSearchActive && query.isNotEmpty()) {
            IconButton(onClick = {
                onQueryChange("")
            }) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Clear search",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
