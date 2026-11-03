"""
Crea la llave de firma de RE UP! Lightstick (se ejecuta UNA sola vez en tu PC).

Uso (Windows, en una carpeta FUERA del proyecto, por ejemplo Documentos):
    py -m pip install cryptography
    py crear_llave.py

Genera:
    reup-release.p12        -> tu llave. GUÁRDALA en un lugar seguro (y una copia).
    secretos-github.txt     -> los dos valores que pegas en GitHub (Settings → Secrets).

IMPORTANTE: si pierdes la llave o la contraseña, las versiones nuevas no se podrán
instalar encima de las anteriores (la gente tendría que desinstalar y reinstalar).
NO subas estos archivos al repositorio.
"""
import base64
import datetime
import os
import secrets
import sys

try:
    from cryptography import x509
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import rsa
    from cryptography.hazmat.primitives.serialization import pkcs12
    from cryptography.x509.oid import NameOID
except ImportError:
    print("Falta la librería. Ejecuta primero:  py -m pip install cryptography")
    sys.exit(1)

KEYFILE = "reup-release.p12"
OUTFILE = "secretos-github.txt"

if os.path.exists(KEYFILE):
    print(f"Ya existe {KEYFILE}. No la sobrescribo (borrarla haría que las actualizaciones no se instalen).")
    sys.exit(1)

password = secrets.token_urlsafe(18)
key = rsa.generate_private_key(public_exponent=65537, key_size=3072)
name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "RE UP! Lightstick")])
now = datetime.datetime.now(datetime.timezone.utc)
cert = (
    x509.CertificateBuilder()
    .subject_name(name)
    .issuer_name(name)
    .public_key(key.public_key())
    .serial_number(x509.random_serial_number())
    .not_valid_before(now - datetime.timedelta(days=1))
    .not_valid_after(now + datetime.timedelta(days=365 * 30))
    .sign(key, hashes.SHA256())
)
data = pkcs12.serialize_key_and_certificates(
    b"reup", key, cert, None, serialization.BestAvailableEncryption(password.encode())
)
with open(KEYFILE, "wb") as f:
    f.write(data)

b64 = base64.b64encode(data).decode()
with open(OUTFILE, "w", encoding="utf-8") as f:
    f.write("Pega estos valores en GitHub → tu repositorio → Settings → Secrets and variables → Actions → New repository secret\n\n")
    f.write("Nombre: REUP_KEYSTORE_PASSWORD\nValor:\n" + password + "\n\n")
    f.write("Nombre: REUP_KEYSTORE_BASE64\nValor (todo, es una sola línea larga):\n" + b64 + "\n")

print("Listo.")
print(f"  Llave:     {os.path.abspath(KEYFILE)}   (guárdala y haz una copia)")
print(f"  Secretos:  {os.path.abspath(OUTFILE)}   (copia los valores a GitHub y luego borra este archivo)")
