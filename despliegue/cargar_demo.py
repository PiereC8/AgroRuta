#!/usr/bin/env python3
"""Crea los negocios de demostración (cada uno con su Dueño, sus operadores y su base propia) con datos de Tacna.

Se ejecuta en el VPS, contra el backend de Render:
    AGRORUTA_API=https://agroruta-api.onrender.com python3 cargar_demo.py /root/agroruta-admin.txt
Usa la API (respeta todas las reglas del sistema) y al final reparte las fechas en los
últimos meses para que los reportes muestren historia. Las claves nuevas se AGREGAN al final de
/root/agroruta-cuentas.txt (solo root puede leerlo); las que ya estaban no se tocan.

- Un negocio que ya existe no se vuelve a crear: solo se le agregan los operadores que le falten
  (entra con la clave de su Dueño guardada en ese archivo).
- sol_de_tacna nunca se toca: es el negocio de la demo en vivo.
- SOLO=negocio1,negocio2 limita la carga a esos negocios.
"""
import json
import os
import random
import secrets
import subprocess
import sys
import urllib.error
import urllib.request
import uuid
from datetime import date, timedelta

API = os.environ.get("AGRORUTA_API", "http://127.0.0.1:8080")
SUFIJO = os.environ.get("BD_SUFIJO", "_prod")
SALIDA = os.environ.get("AGRORUTA_SALIDA", "/root/agroruta-cuentas.txt")
SIN_FECHAS = os.environ.get("SIN_FECHAS") == "1"  # sin cliente mysql: deja las fechas de hoy
# Negocios que el script nunca toca, aunque estén en la lista (la demo en vivo del grupo).
PROTEGIDOS = {"sol_de_tacna"}
random.seed(2026)

# Destinos: (nombre, ciudad, local, lat, lon, flete por kg)
TACNA_GRAU = ("Mercado Grau", "Tacna", True, -18.0105, -70.2486, None)
TACNA_MAYORISTA = ("Gran Mercado Mayorista de Tacna", "Tacna", True, -18.0303, -70.2577, None)
TACNA_2MAYO = ("Mercado 2 de Mayo", "Tacna", True, -18.0128, -70.2531, None)
MOQUEGUA = ("Mercado Central de Moquegua", "Moquegua", False, -17.1955, -70.9347, "0.15")
ILO = ("Mercado Pacocha", "Ilo", False, -17.6394, -71.3375, "0.18")
AREQUIPA = ("Mercado Metropolitano Andrés Avelino Cáceres", "Arequipa", False, -16.4235, -71.5246, "0.25")
AREQUIPA_RIOSECO = ("Mercado Mayorista Río Seco", "Arequipa", False, -16.3646, -71.5687, "0.26")
JULIACA = ("Mercado Túpac Amaru", "Juliaca", False, -15.4934, -70.1318, "0.35")
PUNO = ("Mercado Unión y Dignidad", "Puno", False, -15.8360, -70.0230, "0.34")
LIMA = ("Gran Mercado Mayorista de Lima", "Lima", False, -12.0573, -76.9636, "0.45")

NEGOCIOS = [
    {
        "id": "chacra_calana",
        "nombre": "Chacra Los Durazneros de Calana",
        "plan": "CHACRA",
        "dueno": "rosa.quispe",
        "operadores": ["campo.calana", "ventas.calana"],
        "lotes": 14,
        "kg": (600, 2800),
        "productos": {
            "Durazno blanquillo": 3.20, "Pera de agua": 2.80, "Membrillo": 2.40, "Uva Italia": 4.10,
        },
        "chacras": ["Fundo Santa Rosa, Calana", "Parcela El Huerto, Calana", "Chacra Piedra Blanca, Pocollay"],
        "destinos": [TACNA_GRAU, TACNA_MAYORISTA, MOQUEGUA, AREQUIPA],
        "compradores": [
            ("Comercial Flores e Hijos", "MAYORISTA", "20532719046", "952 314 870", "Tacna"),
            ("Juana Mamani Ticona", "MERCADO", "00482915", "961 228 405", "Tacna"),
            ("Frutas Don Lucho", "MERCADO", "00453118", "943 551 902", "Tacna"),
            ("Distribuidora Moqueguana del Sur", "MAYORISTA", "20601188342", "953 770 114", "Moquegua"),
            ("Frutería La Arequipeña", "MERCADO", "29613047", "959 402 336", "Arequipa"),
            ("Mercedes Coaquira Vargas", "MERCADO", "00477812", "952 307 661", "Tacna"),
        ],
        "transporte": [("Transportes Cutipa", "952 600 431", [("Z1C-718", "4000")]),
                       ("Fletes Calana", "940 118 225", [("Z2B-845", "3500")])],
    },
    {
        "id": "asoc_yarada",
        "nombre": "Asociación de Olivicultores La Yarada Los Palos",
        "plan": "ASOCIACION",
        "dueno": "julio.condori",
        "operadores": ["acopio.yarada", "ventas.yarada"],
        "lotes": 22,
        "kg": (1800, 7500),
        "productos": {
            "Aceituna negra botija": 5.60, "Aceituna verde": 4.80, "Aceituna sevillana": 6.20,
            "Sandía": 1.10, "Melón": 1.50,
        },
        "chacras": ["Parcela 12 – Socio Mamani, La Yarada", "Parcela 27 – Socia Choque, Los Palos",
                    "Parcela 41 – Socio Ramos, La Yarada", "Parcela 8 – Socia Laura, Los Palos",
                    "Parcela 33 – Socio Apaza, Boca del Río"],
        "destinos": [TACNA_MAYORISTA, TACNA_2MAYO, ILO, AREQUIPA_RIOSECO, JULIACA, LIMA],
        "compradores": [
            ("Olivos del Sur E.I.R.L.", "EMPRESA", "20519987451", "052 413 220", "Tacna"),
            ("Conservas La Botija S.A.C.", "EMPRESA", "20604477129", "054 288 913", "Arequipa"),
            ("Inversiones Aceituneras Lima S.A.C.", "MAYORISTA", "20556109873", "01 362 1144", "Lima"),
            ("Comercial Pacocha", "MAYORISTA", "20449120336", "053 482 117", "Ilo"),
            ("Abarrotes Doña Carmen", "MERCADO", "00419372", "952 884 610", "Tacna"),
            ("Distribuidora Altiplano", "MAYORISTA", "20448810452", "951 330 287", "Juliaca"),
            ("Frutas Tropicales Quispe", "MERCADO", "40211876", "967 205 448", "Arequipa"),
            ("Puesto 45 – Hilda Ccallo", "MERCADO", "00492274", "945 670 301", "Tacna"),
            ("Mercado Santa Anita – Puesto 112", "MERCADO", "10456620987", "987 113 540", "Lima"),
        ],
        "transporte": [("Transportes Yarada Express", "952 410 772", [("Z3D-204", "12000"), ("Z3D-205", "12000")]),
                       ("Carga Sur Andina S.R.L.", "054 600 128", [("V5K-913", "20000")]),
                       ("Hermanos Copa", "961 507 339", [("Z1F-662", "8000")])],
    },
    {
        "id": "sol_de_tacna",
        "nombre": "Agroindustrias Sol de Tacna S.A.C.",
        "plan": "EMPRESA",
        "dueno": "carlos.vargas",
        "operadores": ["logistica.sol", "almacen.sol", "ventas.sol"],
        "lotes": 32,
        "kg": (3000, 14000),
        "productos": {
            "Orégano seco": 11.50, "Ají páprika seco": 8.90, "Ajo morado": 6.40, "Cebolla roja": 1.45,
            "Papa amarilla": 1.80, "Aceituna negra botija": 5.70, "Uva Red Globe": 4.60,
        },
        "chacras": ["Fundo Camilaca, Candarave", "Fundo Huanuara, Candarave", "Fundo Magollo, Tacna",
                    "Fundo Sama Las Yaras", "Fundo Locumba, Jorge Basadre", "Fundo Ite, Jorge Basadre",
                    "Fundo La Yarada Lote 5"],
        "destinos": [TACNA_GRAU, TACNA_MAYORISTA, MOQUEGUA, ILO, AREQUIPA, JULIACA, PUNO, LIMA],
        "compradores": [
            ("Especias Andinas del Perú S.A.C.", "EMPRESA", "20510334871", "01 715 2290", "Lima"),
            ("Agroexportadora Pacífico Sur S.A.", "EMPRESA", "20454712098", "054 270 551", "Arequipa"),
            ("Corporación Mayorista Santa Anita S.A.C.", "MAYORISTA", "20601557310", "01 354 8820", "Lima"),
            ("Supermercados del Sur S.A.", "EMPRESA", "20120988431", "054 389 400", "Arequipa"),
            ("Distribuidora Titicaca E.I.R.L.", "MAYORISTA", "20448199536", "051 366 214", "Puno"),
            ("Comercial Juliaca Norte", "MAYORISTA", "20605512744", "951 778 316", "Juliaca"),
            ("Minimarket Bolognesi", "MERCADO", "10004521897", "952 113 908", "Tacna"),
            ("Agrícola Moquegua S.A.C.", "EMPRESA", "20532861105", "053 461 772", "Moquegua"),
            ("Puesto 8 – Víctor Chambilla", "MERCADO", "00468213", "944 201 556", "Tacna"),
            ("Pesquera y Abarrotes Ilo", "MAYORISTA", "20413560027", "053 781 045", "Ilo"),
            ("Restaurante El Sabor Tacneño", "MERCADO", "20602318874", "052 427 690", "Tacna"),
            ("Bodega Doña Felícita", "MERCADO", "00436590", "952 660 873", "Tacna"),
        ],
        "transporte": [("Transportes Sol de Tacna", "052 315 400", [("Z4A-110", "30000"), ("Z4A-111", "30000")]),
                       ("Carga Sur Andina S.R.L.", "054 600 128", [("V5K-913", "25000")]),
                       ("Transportes Mamani Hnos.", "952 778 004", [("Z2E-487", "15000"), ("Z2E-488", "15000")]),
                       ("Logística Altiplano", "951 220 615", [("Z6G-302", "20000")])],
    },
    # ---- Agregados el 2026-11-17: cuatro negocios más, cada uno con Dueño y operadores logísticos ----
    {
        "id": "coop_candarave",
        "nombre": "Cooperativa Agraria del Orégano de Candarave",
        "plan": "ASOCIACION",
        "dueno": "elena.ticona",
        "operadores": ["acopio.candarave", "despacho.candarave"],
        "lotes": 18,
        "kg": (800, 4500),
        "productos": {
            "Orégano seco": 11.20, "Papa nativa": 2.10, "Haba verde": 2.60, "Maíz amiláceo": 3.40,
        },
        "chacras": ["Parcela Camilaca – Socio Ticona", "Parcela Cairani – Socia Flores",
                    "Parcela Huanuara – Socio Copa", "Parcela Quilahuani – Socia Mamani"],
        "destinos": [TACNA_MAYORISTA, MOQUEGUA, AREQUIPA_RIOSECO, JULIACA, LIMA],
        "compradores": [
            ("Especiería Los Andes S.A.C.", "EMPRESA", "20608814527", "01 652 3318", "Lima"),
            ("Comercial Río Seco", "MAYORISTA", "20455890213", "054 312 765", "Arequipa"),
            ("Abarrotes San Martín", "MERCADO", "00471266", "952 330 814", "Tacna"),
            ("Distribuidora Altiplano", "MAYORISTA", "20448810452", "951 330 287", "Juliaca"),
            ("Mercado Central – Puesto 23", "MERCADO", "04412983", "953 118 640", "Moquegua"),
            ("Condimentos del Sur E.I.R.L.", "EMPRESA", "20609932175", "052 401 557", "Tacna"),
            ("Puesto 67 – Rufina Apaza", "MERCADO", "00488510", "944 520 176", "Tacna"),
        ],
        "transporte": [("Transportes Candarave", "952 801 344", [("Z5B-731", "6000"), ("Z5B-732", "6000")]),
                       ("Fletes Altoandinos", "951 642 078", [("V2H-415", "10000")])],
    },
    {
        "id": "fundo_magollo",
        "nombre": "Fundo Magollo Agroexport S.A.C.",
        "plan": "EMPRESA",
        "dueno": "ricardo.pari",
        "operadores": ["logistica.magollo", "almacen.magollo", "ventas.magollo"],
        "lotes": 26,
        "kg": (2500, 12000),
        "productos": {
            "Uva Red Globe": 4.70, "Uva Italia": 4.20, "Granada": 3.90, "Palta Hass": 6.80,
        },
        "chacras": ["Fundo Magollo Sector A, Tacna", "Fundo Magollo Sector B, Tacna", "Fundo Pocollay, Tacna"],
        "destinos": [TACNA_MAYORISTA, ILO, AREQUIPA, PUNO, LIMA],
        "compradores": [
            ("Frutas Selectas del Pacífico S.A.C.", "EMPRESA", "20611245098", "01 718 4420", "Lima"),
            ("Mercado Mayorista N.° 2 – Puesto 310", "MAYORISTA", "20557743016", "01 354 7712", "Lima"),
            ("Supermercados Arequipa Sur S.A.", "EMPRESA", "20120975564", "054 381 905", "Arequipa"),
            ("Comercial Lago Titicaca", "MAYORISTA", "20448307791", "051 368 442", "Puno"),
            ("Distribuidora Portuaria Ilo", "MAYORISTA", "20413678810", "053 784 219", "Ilo"),
            ("Frutería Bolognesi", "MERCADO", "10004893326", "952 207 315", "Tacna"),
            ("Juan Carlos Huanacuni", "MERCADO", "00459031", "961 438 207", "Tacna"),
            ("Hoteles y Eventos Tacna S.A.C.", "EMPRESA", "20602715460", "052 426 118", "Tacna"),
        ],
        "transporte": [("Transportes Magollo", "052 318 660", [("Z7C-120", "20000"), ("Z7C-121", "20000")]),
                       ("Refrigerados del Sur S.A.C.", "054 612 309", [("V8L-552", "25000")]),
                       ("Transportes Quispe Hnos.", "952 771 486", [("Z2F-903", "15000")])],
    },
    {
        "id": "chacra_sama",
        "nombre": "Chacra Valle de Sama",
        "plan": "CHACRA",
        "dueno": "lucia.choque",
        "operadores": ["campo.sama"],
        "lotes": 12,
        "kg": (500, 2400),
        "productos": {
            "Ají amarillo": 3.60, "Cebolla roja": 1.40, "Maíz choclo": 1.90, "Zapallo macre": 1.10,
        },
        "chacras": ["Chacra Las Yaras, Sama", "Chacra Inclán, Sama"],
        "destinos": [TACNA_GRAU, TACNA_2MAYO, MOQUEGUA, ILO],
        "compradores": [
            ("Verdulería Doña Rosa", "MERCADO", "00463917", "952 615 203", "Tacna"),
            ("Comercial Inclán", "MAYORISTA", "20532980441", "952 448 916", "Tacna"),
            ("Mercado Pacocha – Puesto 14", "MERCADO", "04437128", "953 207 661", "Ilo"),
            ("Distribuidora Moqueguana del Sur", "MAYORISTA", "20601188342", "953 770 114", "Moquegua"),
            ("Restaurante La Cabaña Sameña", "MERCADO", "10442817330", "961 902 445", "Tacna"),
        ],
        "transporte": [("Fletes Sama", "952 309 871", [("Z1G-204", "3000")]),
                       ("Transportes Yaras", "940 772 158", [("Z1G-377", "5000")])],
    },
    {
        "id": "agro_locumba",
        "nombre": "Agrícola Valle de Locumba E.I.R.L.",
        "plan": "EMPRESA",
        "dueno": "jorge.alanoca",
        "operadores": ["logistica.locumba", "ventas.locumba"],
        "lotes": 20,
        "kg": (2000, 9000),
        "productos": {
            "Ají páprika seco": 8.80, "Ajo morado": 6.20, "Cebolla roja": 1.50, "Uva Quebranta": 3.80,
        },
        "chacras": ["Fundo Locumba Bajo, Jorge Basadre", "Fundo Ilabaya, Jorge Basadre",
                    "Fundo Ite Alto, Jorge Basadre"],
        "destinos": [TACNA_MAYORISTA, MOQUEGUA, ILO, AREQUIPA, LIMA],
        "compradores": [
            ("Agroexportadora Costa Sur S.A.C.", "EMPRESA", "20610377952", "054 274 118", "Arequipa"),
            ("Especias y Condimentos Lima S.A.C.", "EMPRESA", "20556284017", "01 715 9036", "Lima"),
            ("Bodegas Pisqueras del Valle", "EMPRESA", "20605219884", "053 462 790", "Moquegua"),
            ("Comercial Pacocha", "MAYORISTA", "20449120336", "053 482 117", "Ilo"),
            ("Mercado Grau – Puesto 52", "MERCADO", "00428893", "952 116 504", "Tacna"),
            ("Distribuidora Basadre", "MAYORISTA", "20532447106", "952 683 290", "Tacna"),
        ],
        "transporte": [("Transportes Locumba", "052 781 245", [("Z8D-610", "15000"), ("Z8D-611", "15000")]),
                       ("Carga Sur Andina S.R.L.", "054 600 128", [("V5K-913", "25000")]),
                       ("Fletes Ilabaya", "953 440 167", [("Z3H-218", "8000")])],
    },
]


class Api:
    def __init__(self, token=None):
        self.token = token

    def llamar(self, metodo, ruta, cuerpo=None, encabezados=None, permitir=()):
        """permitir: códigos HTTP que no detienen el script (devuelve None), p. ej. 409 si ya existe."""
        datos = None if cuerpo is None else json.dumps(cuerpo).encode()
        req = urllib.request.Request(API + ruta, data=datos, method=metodo)
        req.add_header("Content-Type", "application/json")
        if self.token:
            req.add_header("Authorization", "Bearer " + self.token)
        for k, v in (encabezados or {}).items():
            req.add_header(k, v)
        try:
            with urllib.request.urlopen(req, timeout=900) as r:
                texto = r.read().decode()
                return json.loads(texto) if texto else None
        except urllib.error.HTTPError as e:
            if e.code in permitir:
                return None
            raise SystemExit(f"{metodo} {ruta} -> {e.code}: {e.read().decode()[:400]}")


def login(negocio, usuario, clave):
    r = Api().llamar("POST", "/api/auth/login",
                     {"identificadorNegocio": negocio, "nombreUsuario": usuario, "clave": clave})
    if r.get("requiereCodigo"):
        # Verificación en dos pasos: un Dueño con correo recibe un código y el script no puede seguir.
        raise SystemExit(f"{negocio}: el Dueño {usuario} tiene correo y le llega un código para entrar. "
                         "Crea sus operadores desde la pantalla Equipo o quítale el correo antes de correr el script.")
    return Api(r["token"])


def clave_guardada(negocio, usuario):
    """La clave de una cuenta ya creada, leída del archivo de cuentas (la última que aparezca)."""
    if not os.path.exists(SALIDA):
        return None
    clave, en_negocio = None, False
    with open(SALIDA, encoding="utf-8") as f:
        for linea in f:
            if "Código del negocio:" in linea:
                en_negocio = linea.split(":", 1)[1].strip() == negocio
            elif en_negocio and f"usuario: {usuario} " in linea + " " and "clave:" in linea:
                clave = linea.rsplit("clave:", 1)[1].strip()
    return clave


def completar_operadores(n):
    """Negocio que ya existe: le agrega los operadores que le falten. Devuelve las cuentas nuevas."""
    clave_dueno = clave_guardada(n["id"], n["dueno"])
    if not clave_dueno:
        print(f"{n['id']}: ya existe, pero no está la clave de {n['dueno']} en {SALIDA}; agrega sus operadores "
              "desde la pantalla Equipo.")
        return []
    api = login(n["id"], n["dueno"], clave_dueno)
    existentes = {m["nombreUsuario"] for m in api.llamar("GET", "/api/equipo")}
    cuentas = []
    for op in n["operadores"]:
        if op in existentes:
            continue
        c = clave_nueva("Operador")
        api.llamar("POST", "/api/equipo", {"nombreUsuario": op, "clave": c, "correo": None})
        cuentas.append((op, "Operador", c))
    return cuentas


def clave_nueva(palabra):
    return f"{palabra}-{secrets.randbelow(9000) + 1000}"


def kg(n):
    return f"{n:.3f}"


def mysql(sql):
    subprocess.run(["mysql", "-e", sql], check=True)


def cargar(admin, n):
    """Devuelve (cuentas nuevas, resumen). Si el negocio ya existe, solo completa sus operadores."""
    cuentas = []
    clave_dueno = clave_nueva(n["id"].split("_")[-1].capitalize() + "Tacna")
    creado = admin.llamar("POST", "/api/plataforma/negocios", {
        "identificadorNegocio": n["id"], "nombreNegocio": n["nombre"], "planSuscripcion": n["plan"],
        "nombreUsuarioDueno": n["dueno"], "claveInicialDueno": clave_dueno}, permitir=(409,))
    if creado is None:
        nuevos = completar_operadores(n)
        guardar_cuentas(n, nuevos)
        return nuevos, {"ya_existia": True, "operadores_nuevos": [c[0] for c in nuevos]}
    cuentas.append((n["dueno"], "Dueño", clave_dueno))
    api = login(n["id"], n["dueno"], clave_dueno)

    for op in n["operadores"]:
        c = clave_nueva("Operador")
        api.llamar("POST", "/api/equipo", {"nombreUsuario": op, "clave": c, "correo": None})
        cuentas.append((op, "Operador", c))
    # Las claves se guardan apenas existen las cuentas: si algo falla después, no se pierden.
    guardar_cuentas(n, cuentas)

    destinos = []
    productos = list(n["productos"].items())
    for nombre, ciudad, local, lat, lon, flete in n["destinos"]:
        d = api.llamar("POST", "/api/destinos", {"nombreDestino": nombre, "ciudadDestino": ciudad, "esLocal": local})
        idd = d["idDestinoVenta"]
        api.llamar("PUT", f"/api/destinos/{idd}/coordenadas", {"latitud": lat, "longitud": lon})
        if flete:
            api.llamar("PUT", f"/api/destinos/{idd}/tarifa", {"costoFletePorKg": flete})
        # Lejos se paga más: el precio sube con el flete.
        extra = 0 if local else float(flete) * 1.6
        for prod, base in productos:
            precio = round(base * random.uniform(0.95, 1.08) + extra, 2)
            api.llamar("PUT", f"/api/destinos/{idd}/precio", {"productoLote": prod, "precioPorKg": f"{precio:.2f}"})
        destinos.append((idd, ciudad, local, extra))

    compradores = []
    for nombre, tipo, doc, tel, ciudad in n["compradores"]:
        c = api.llamar("POST", "/api/compradores", {"nombreComprador": nombre, "tipoComprador": tipo,
                                                    "documentoComprador": doc, "telefonoComprador": tel,
                                                    "ciudadComprador": ciudad})
        compradores.append((c["idComprador"], ciudad))

    vehiculos = []
    for nombre, tel, unidades in n["transporte"]:
        t = api.llamar("POST", "/api/transportistas", {"nombreTransportista": nombre, "telefonoTransportista": tel})
        for placa, cap in unidades:
            v = api.llamar("POST", "/api/vehiculos", {"idTransportista": t["idTransportista"],
                                                      "placaVehiculo": placa, "capacidadKg": cap})
            vehiculos.append((v["idVehiculo"], float(cap)))
    # Ningún lote pasa la capacidad del vehículo más grande: si no, no se podría despachar.
    capacidad_maxima = max(cap for _, cap in vehiculos)

    total = n["lotes"]
    # Reparto de estados: la mayoría ya liquidados; algunos en camino, despachados, con pedido o recién cosechados.
    en_ruta = min(2, len(vehiculos) - 1) if len(vehiculos) > 2 else 1
    despachados = min(len(vehiculos) - en_ruta, 2)
    con_pedido = max(2, total // 10)
    solo_lote = max(2, total // 10)
    entregados = total - en_ruta - despachados - con_pedido - solo_lote

    hoy = date.today()
    entregas = []  # (id_lote, dias_atras)
    lotes_info = []
    for i in range(total):
        prod, _ = productos[i % len(productos)]
        cantidad = min(random.randrange(n["kg"][0], n["kg"][1], 50), int(capacidad_maxima))
        lote = api.llamar("POST", "/api/lotes", {
            "productoLote": prod, "cantidadKg": kg(cantidad),
            "fechaCosecha": (hoy - timedelta(days=random.randint(1, 3))).isoformat(),
            "chacraOrigen": random.choice(n["chacras"])})
        lotes_info.append((lote["idLote"], prod, cantidad))

    estados = (["entregado"] * entregados + ["en_ruta"] * en_ruta + ["despachado"] * despachados
               + ["pedido"] * con_pedido + ["lote"] * solo_lote)
    vehiculo_libre = list(vehiculos)
    pagos = {"tarjeta": 0, "pagoefectivo_pendiente": 0, "sin_pagar": 0}
    for idx, ((id_lote, prod, cantidad), estado) in enumerate(zip(lotes_info, estados)):
        if estado == "lote":
            continue
        id_dest, ciudad, local, extra = random.choice(destinos)
        candidatos = [c for c in compradores if c[1] == ciudad] or compradores
        id_comp = random.choice(candidatos)[0]
        precio = round(n["productos"][prod] * random.uniform(0.97, 1.06) + extra, 2)
        pedido = api.llamar("POST", "/api/pedidos", {"idComprador": id_comp, "idLote": id_lote,
                                                     "idDestinoVenta": id_dest, "precioAcordadoPorKg": f"{precio:.2f}"})
        if estado == "pedido":
            continue
        # Un vehículo que cargue el pedido. Los que quedan en ruta o despachados ya no se reusan (un envío activo
        # por vehículo); si no queda ninguno que alcance, el lote se queda con su pedido sin despachar.
        if estado == "entregado":
            aptos = [v for v in vehiculos if v[1] >= cantidad]
            vehiculo = aptos[idx % len(aptos)]
        else:
            aptos = [v for v in vehiculo_libre if v[1] >= cantidad]
            if not aptos:
                continue
            vehiculo = aptos[0]
            vehiculo_libre.remove(vehiculo)
        envio = api.llamar("POST", "/api/envios", {"idPedido": pedido["idPedido"], "idVehiculo": vehiculo[0]})
        if estado == "despachado":
            continue
        api.llamar("POST", f"/api/envios/{envio['idEnvio']}/salida")
        if estado == "en_ruta":
            api.llamar("POST", f"/api/envios/{envio['idEnvio']}/enlaces")
            continue
        merma = round(cantidad * random.uniform(0.004, 0.03))
        rechazo = round(cantidad * random.uniform(0.0, 0.02)) if random.random() < 0.35 else 0
        api.llamar("POST", f"/api/envios/{envio['idEnvio']}/entrega", {
            "cantidadRecibidaKg": kg(cantidad - merma - rechazo), "mermaKg": kg(merma), "rechazoKg": kg(rechazo)})
        # Lo más antiguo primero: así la numeración de comprobantes sigue el orden de las fechas.
        dias = int((entregados - idx) * 165 / max(entregados, 1)) + random.randint(0, 3)
        entregas.append((id_lote, dias))

        ultimos = idx >= entregados - 3
        if ultimos and idx == entregados - 1:
            pagos["sin_pagar"] += 1
            continue
        enl = api.llamar("POST", f"/api/envios/{envio['idEnvio']}/enlaces")
        publico = Api()
        cabeceras = {"X-Token-Seguimiento": enl["tokenComprador"], "Idempotency-Key": str(uuid.uuid4())}
        if ultimos and idx == entregados - 2:
            publico.llamar("POST", "/api/publico/pago", {"tokenTarjeta": "sim_pagoefectivo",
                                                         "email": "compras@example.com", "metodoPago": "pagoefectivo"},
                           cabeceras)
            pagos["pagoefectivo_pendiente"] += 1
            continue
        marca = random.choice([("visa", "4242"), ("mastercard", "5454"), ("paypal", None)])
        token = f"sim_aprobada_{marca[0]}" + (f"_{marca[1]}" if marca[1] else "")
        metodo = "paypal" if marca[0] == "paypal" else marca[0]
        publico.llamar("POST", "/api/publico/pago", {"tokenTarjeta": token, "email": "compras@example.com",
                                                     "metodoPago": metodo}, cabeceras)
        pagos["tarjeta"] += 1

    if not SIN_FECHAS:
        retroceder_fechas(n["id"], entregas)
    return cuentas, {"lotes": total, "entregados": entregados, "en_ruta": en_ruta, "despachados": despachados,
                     "con_pedido": con_pedido, "solo_lote": solo_lote, **pagos,
                     "destinos": len(destinos), "compradores": len(compradores), "vehiculos": len(vehiculos)}


def retroceder_fechas(id_negocio, entregas):
    base = f"agroruta_{id_negocio}{SUFIJO}"
    sentencias = [f"USE `{base}`;"]
    for id_lote, d in entregas:
        sentencias += [
            f"UPDATE lote SET fecha_registro = fecha_registro - INTERVAL {d + 3} DAY WHERE id_lote = {id_lote};",
            f"UPDATE lote SET fecha_cosecha = DATE(fecha_registro) - INTERVAL 2 DAY WHERE id_lote = {id_lote};",
            f"UPDATE evento_lote SET fecha_evento = fecha_evento - INTERVAL (CASE tipo_evento "
            f"WHEN 'REGISTRO' THEN {d + 3} WHEN 'RESERVA' THEN {d + 2} WHEN 'DESPACHO' THEN {d + 1} "
            f"ELSE {d} END) DAY WHERE id_lote = {id_lote};",
            f"UPDATE pedido SET fecha_pedido = fecha_pedido - INTERVAL {d + 2} DAY WHERE id_lote = {id_lote};",
            f"UPDATE envio SET fecha_programada = fecha_programada - INTERVAL {d + 1} DAY, "
            f"fecha_salida = fecha_salida - INTERVAL {d + 1} DAY, fecha_entrega = fecha_entrega - INTERVAL {d} DAY "
            f"WHERE id_lote = {id_lote};",
            f"UPDATE liquidacion SET fecha_entrega = fecha_entrega - INTERVAL {d} DAY WHERE id_lote = {id_lote};",
            f"UPDATE pago p JOIN pedido pe ON pe.id_pedido = p.id_pedido SET "
            f"p.fecha_creacion = p.fecha_creacion - INTERVAL {d} DAY + INTERVAL 5 HOUR, "
            f"p.fecha_actualizacion = p.fecha_actualizacion - INTERVAL {d} DAY + INTERVAL 5 HOUR "
            f"WHERE pe.id_lote = {id_lote} AND p.estado_pago = 'APROBADO';",
            f"UPDATE comprobante c JOIN pago p ON p.id_pago = c.id_pago JOIN pedido pe ON pe.id_pedido = p.id_pedido "
            f"SET c.fecha_emision = DATE(p.fecha_creacion), c.fecha_creacion = p.fecha_creacion "
            f"WHERE pe.id_lote = {id_lote};",
        ]
    mysql("\n".join(sentencias))


def main():
    with open(sys.argv[1], encoding="utf-8") as f:
        datos = dict(l.split(":", 1) for l in f.read().splitlines() if ":" in l)
    admin = login(None, datos["Usuario"].strip(), datos["Clave"].strip())
    solo = {x.strip() for x in os.environ.get("SOLO", "").split(",") if x.strip()}
    for n in [x for x in NEGOCIOS if not solo or x["id"] in solo]:
        if n["id"] in PROTEGIDOS:
            print(n["id"], "no se toca (demo en vivo)")
            continue
        _, resumen = cargar(admin, n)
        print(n["id"], n["plan"], json.dumps(resumen, ensure_ascii=False))
    print("Claves nuevas agregadas al final de", SALIDA)


def guardar_cuentas(n, cuentas):
    """Agrega las cuentas al final del archivo (las que ya estaban no se tocan) en cuanto se crean."""
    if not cuentas:
        return
    lineas = ["", f"Plan {n['plan']} – {n['nombre']} (agregado el {date.today().isoformat()})",
              f"  Código del negocio: {n['id']}"]
    for usuario, rol, clave in cuentas:
        lineas.append(f"  {rol:9} usuario: {usuario:16} clave: {clave}")
    with open(SALIDA, "a", encoding="utf-8") as f:
        f.write("\n".join(lineas) + "\n")
    os.chmod(SALIDA, 0o600)


if __name__ == "__main__":
    main()
