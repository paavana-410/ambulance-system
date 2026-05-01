import os

path = r'c:\Users\kittu\Downloads\opt\ambulance1\ambulance\Driver_app\app\src\main\java\com\smartambulance\patient\MainActivity.kt'

with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Update AppNavigation LaunchedEffect
content = content.replace(
    'if (authStatus.role == "driver") {',
    'if (authStatus.role == "driver") {\n                    navController.navigate("driver_home") { popUpTo(0) }'
)

# 2. Update NavHost
content = content.replace(
    'composable("splash") { SplashScreen(navController, authViewModel) }',
    '''composable("splash") { SplashScreen(navController, authViewModel) }
        composable("driver_login") { DriverLoginScreen(navController, authViewModel) }
        composable("driver_register") { DriverRegisterScreen(navController, authViewModel) }'''
)

# 3. Update SplashScreen navigation
content = content.replace(
    'navController.navigate("email") { popUpTo("splash") { inclusive = true } }',
    'navController.navigate("driver_login") { popUpTo("splash") { inclusive = true } }'
)

# 4. Add DriverLoginScreen and DriverRegisterScreen
# We'll replace DriverProfileScreen with these two.
new_screens = """@Composable
fun DriverLoginScreen(navController: NavController, viewModel: AuthViewModel) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val status = viewModel.authStatus

    Column(
        modifier = Modifier.fillMaxSize().background(DeepPurple).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("ResQG Driver", fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, color = ResQGRed, fontFamily = FontFamily.Serif)
        Text("Login to your dashboard", color = Color.White, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(32.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(15.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = status !is AuthStatus.Loading
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = status !is AuthStatus.Loading
                )
                
                if (status is AuthStatus.Error) {
                    Text(status.message, color = ResQGRed, modifier = Modifier.padding(top = 8.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { viewModel.loginDriver(username, password) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DeepPurple),
                    enabled = status !is AuthStatus.Loading && username.isNotBlank() && password.isNotBlank()
                ) {
                    if (status is AuthStatus.Loading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text("LOGIN", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(
                    onClick = { navController.navigate("driver_register") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Don't have an account? Register here", color = DeepPurple)
                }
            }
        }
    }
}

@Composable
fun DriverRegisterScreen(navController: NavController, viewModel: AuthViewModel) {
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var ambulanceNo by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    val status = viewModel.authStatus

    Column(
        modifier = Modifier.fillMaxSize().background(DeepPurple).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        Text("Driver Registration", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(24.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(15.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(value = ambulanceNo, onValueChange = { ambulanceNo = it }, label = { Text("Ambulance No.") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number") }, modifier = Modifier.fillMaxWidth())
                
                if (status is AuthStatus.Error) {
                    Text(status.message, color = ResQGRed, modifier = Modifier.padding(top = 8.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { viewModel.registerDriver(name, username, password, ambulanceNo, phone) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ResQGRed),
                    enabled = status !is AuthStatus.Loading
                ) {
                    if (status is AuthStatus.Loading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text("REGISTER", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                
                TextButton(
                    onClick = { navController.navigate("driver_login") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Already registered? Login here", color = DeepPurple)
                }
            }
        }
    }
}
"""

# Find DriverProfileScreen and replace it
import re
pattern = re.compile(r'@Composable\s+fun DriverProfileScreen\(.*?\)\s+\{.*?^\}', re.MULTILINE | re.DOTALL)
content = pattern.sub(new_screens, content, count=1)

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)

print("Replacement successful")
