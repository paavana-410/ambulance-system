import os

path = r'c:\Users\kittu\Downloads\opt\ambulance1\ambulance\Driver_app\app\src\main\java\com\smartambulance\patient\MainActivity.kt'

with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Clean up AppNavigation LaunchedEffect
content = content.replace(
    '''                if (authStatus.role == "driver") {
                    navController.navigate("driver_home") { popUpTo(0) }
                    if (authStatus.isProfileComplete) {
                        navController.navigate("driver_home") { popUpTo(0) }
                    } else {
                        navController.navigate("driver_profile") { popUpTo(0) }
                    }
                } else {''',
    '''                if (authStatus.role == "driver") {
                    navController.navigate("driver_home") { popUpTo(0) }
                } else {'''
)

# 2. Fix driver logout navigation
content = content.replace(
    'navController.navigate("email") { popUpTo(0) }',
    'navController.navigate("driver_login") { popUpTo(0) }'
)

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)

print("Cleanup successful")
