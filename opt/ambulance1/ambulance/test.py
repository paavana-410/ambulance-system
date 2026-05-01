import sys
path = 'Patient_app/app/src/main/java/com/smartambulance/patient/MainActivity.kt'
with open(path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

print("File has", len(lines), "lines.")
