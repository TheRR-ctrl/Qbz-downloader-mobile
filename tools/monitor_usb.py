#!/usr/bin/env python3
"""
Vigila el teléfono desde el PC por USB (adb) y guarda todo en disco, para que si el teléfono
se congela o se apaga quede el rastro en el PC. Solo usa la biblioteca estándar de Python 3.

Antes (una vez):
  1. En el teléfono: Ajustes > Acerca del teléfono > pulsar 7 veces «Número de compilación»,
     luego Ajustes > Opciones de desarrollador > activar «Depuración USB».
  2. En el PC: instalar «platform-tools» de Android (trae adb) y que `adb` esté en el PATH.
  3. Conectar el cable y aceptar «¿Permitir depuración USB?» en el teléfono.

Uso:   python3 monitor_usb.py            (déjalo corriendo mientras escuchas música)
       Ctrl+C para terminar.
Crea la carpeta monitor-<fecha>/ con:
  logcat.txt   todo el log de Android, sin parar (se reanuda si el cable se corta o el teléfono reinicia)
  estado.txt   cada 2 s: batería, temperatura, estado térmico, uso de memoria y de CPU del teléfono
  eventos.txt  cuándo se perdió y volvió la conexión (la hora en que se calla estado.txt es el cuelgue)
  app-diag/    el registro por segundo de la app (sesiones guardadas en el teléfono), al volver la conexión
"""
import datetime
import os
import subprocess
import sys
import threading
import time

PAQUETE = "com.qbdlx.mobile"
CONSULTA = (
    "dumpsys battery | grep -E 'level|temperature|status|powered'; "
    "dumpsys thermalservice | grep -iE 'status|temp' | head -25; "
    "for z in /sys/class/thermal/thermal_zone*; do echo \"$(cat $z/type 2>/dev/null) $(cat $z/temp 2>/dev/null)\"; done; "
    "head -3 /proc/meminfo; cat /proc/loadavg"
)


def ahora():
    return datetime.datetime.now().strftime("%H:%M:%S.%f")[:-3]


def adb(*args, timeout=8):
    return subprocess.run(["adb", *args], capture_output=True, text=True, timeout=timeout, errors="replace")


def conectado():
    try:
        return adb("get-state", timeout=4).stdout.strip() == "device"
    except Exception:
        return False


class Archivo:
    def __init__(self, ruta):
        self.f = open(ruta, "a", encoding="utf-8", errors="replace")
        self.cerrojo = threading.Lock()

    def escribe(self, texto, fsync=False):
        with self.cerrojo:
            self.f.write(texto)
            self.f.flush()
            if fsync:
                os.fsync(self.f.fileno())


def hilo_logcat(carpeta, parar):
    salida = Archivo(os.path.join(carpeta, "logcat.txt"))
    while not parar.is_set():
        if not conectado():
            time.sleep(1)
            continue
        salida.escribe(f"===== {ahora()} logcat (re)empieza =====\n")
        p = subprocess.Popen(["adb", "logcat", "-b", "all", "-v", "threadtime"],
                             stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, errors="replace")
        try:
            for linea in p.stdout:
                salida.escribe(linea)
                if parar.is_set():
                    break
        finally:
            p.kill()
        salida.escribe(f"===== {ahora()} logcat cortado =====\n")
        time.sleep(1)


def trae_registros_de_la_app(carpeta, eventos):
    destino = os.path.join(carpeta, "app-diag")
    os.makedirs(destino, exist_ok=True)
    try:
        lista = adb("shell", f"run-as {PAQUETE} ls files/diag", timeout=10).stdout.split()
        for nombre in lista:
            r = subprocess.run(["adb", "exec-out", f"run-as {PAQUETE} cat files/diag/{nombre}"],
                               capture_output=True, timeout=30)
            with open(os.path.join(destino, nombre), "wb") as f:
                f.write(r.stdout)
        eventos.escribe(f"{ahora()} traídos {len(lista)} registros de la app\n", True)
    except Exception as e:
        eventos.escribe(f"{ahora()} no se pudieron traer los registros de la app: {e}\n", True)


def main():
    try:
        adb("version")
    except FileNotFoundError:
        sys.exit("No encuentro «adb». Instala platform-tools de Android y ponlo en el PATH.")
    carpeta = "monitor-" + datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    os.makedirs(carpeta, exist_ok=True)
    estado = Archivo(os.path.join(carpeta, "estado.txt"))
    eventos = Archivo(os.path.join(carpeta, "eventos.txt"))
    parar = threading.Event()
    threading.Thread(target=hilo_logcat, args=(carpeta, parar), daemon=True).start()
    print(f"Guardando en {carpeta}/  ·  Ctrl+C para terminar")
    estaba = None
    try:
        while True:
            ok = conectado()
            if ok != estaba:
                eventos.escribe(f"{ahora()} {'CONECTADO' if ok else 'SIN CONEXIÓN con el teléfono'}\n", True)
                print(ahora(), "conectado" if ok else "SIN CONEXIÓN")
                if ok and estaba is False:
                    time.sleep(3)
                    trae_registros_de_la_app(carpeta, eventos)
                estaba = ok
            if ok:
                try:
                    t = adb("shell", CONSULTA, timeout=6)
                    estado.escribe(f"--- {ahora()}\n{t.stdout}", True)
                except Exception as e:
                    estado.escribe(f"--- {ahora()} consulta fallida: {e}\n", True)
            time.sleep(2)
    except KeyboardInterrupt:
        pass
    parar.set()
    if conectado():
        trae_registros_de_la_app(carpeta, eventos)
    print("Terminado. Comprime la carpeta", carpeta, "y pásamela.")


if __name__ == "__main__":
    main()
