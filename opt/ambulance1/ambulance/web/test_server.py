from flask import Flask
app = Flask(__name__)

@app.route('/')
def home():
    return "TEST SERVER IS ONLINE"

@app.route('/api/request_ambulance', methods=['GET', 'POST'])
def test_sos():
    return "SOS ROUTE IS WORKING - NO ERROR"

if __name__ == '__main__':
    print("!!! TEST SERVER STARTING ON PORT 3000 !!!")
    app.run(host='0.0.0.0', port=3000)
