# Laboratorio de Virtualización — Contenerización y Despliegue de una Aplicación Web Java

**Autora:** Ana Gabriela Fiquitiva Poveda
**Contexto académico:** Taller TDSE — Contenerización y despliegue de una aplicación web Java (Spring Boot)

Un servicio REST mínimo en Spring Boot, usado como vehículo para estudiar la virtualización como mecanismo arquitectónico de **modularidad, aislamiento, portabilidad y despliegue**. El mismo artefacto de construcción se ejecuta sin modificaciones como proceso local de la JVM, como contenedor Docker aislado (en una o varias instancias concurrentes), y como servicio en una máquina virtual EC2 de AWS — la configuración cambia únicamente mediante variables de entorno, nunca mediante código o recompilaciones.

## Tabla de contenido

1. [Propósito y objetivos de aprendizaje](#propósito-y-objetivos-de-aprendizaje)
2. [Línea base tecnológica](#línea-base-tecnológica)
3. [Arquitectura y diseño de clases](#arquitectura-y-diseño-de-clases)
4. [Estructura del proyecto](#estructura-del-proyecto)
5. [Parte 1 — Construcción y ejecución local](#parte-1--construcción-y-ejecución-local)
6. [Parte 2 — Contenerización y aislamiento de procesos](#parte-2--contenerización-y-aislamiento-de-procesos)
7. [Parte 3 — Orquestación declarativa con Docker Compose](#parte-3--orquestación-declarativa-con-docker-compose)
8. [Parte 4 — Publicación en Docker Hub](#parte-4--publicación-en-docker-hub)
9. [Parte 5 — Despliegue en AWS EC2](#parte-5--despliegue-en-aws-ec2)
10. [Parte 6 — Modelo de despliegue y análisis de costos](#parte-6--modelo-de-despliegue-y-análisis-de-costos)
11. [Video de demostración](#video-de-demostración)
12. [Checklist de entregables](#checklist-de-entregables)
13. [Índice de evidencias](#índice-de-evidencias)
14. [Notas sobre la construcción](#notas-sobre-la-construcción)

## Propósito y objetivos de aprendizaje

La virtualización no es una sola tecnología, sino un espectro de mecanismos —desde la emulación completa de hardware hasta el aislamiento de procesos a nivel de sistema operativo— que resuelven el mismo problema de fondo: desacoplar *qué* hace el software de *dónde* y *cómo* se ejecuta. Este taller recorre ese espectro de extremo a extremo usando una sola aplicación deliberadamente pequeña, de modo que cualquier diferencia observada entre entornos de ejecución pueda atribuirse al entorno mismo y no a la complejidad de la aplicación.

En concreto, este repositorio demuestra:

- La construcción de una aplicación web Java cuya configuración de tiempo de ejecución (el puerto de escucha) se externaliza al entorno en lugar de quedar fija en el código — el principio de configuración de [Twelve-Factor App](https://12factor.net/config).
- El empaquetado de esa aplicación como una imagen Docker inmutable, de modo que el artefacto que se prueba sea el artefacto que se despliega.
- La ejecución de varias instancias de esa imagen de forma aislada en un mismo host, para observar el aislamiento de contenedores directamente en lugar de asumirlo.
- La publicación de la imagen en un registro público (Docker Hub), desacoplando *dónde se construye una imagen* de *dónde se ejecuta*.
- El despliegue de esa misma imagen en una máquina virtual EC2 de AWS y la verificación de que es alcanzable desde internet público.
- El razonamiento cuantitativo sobre cuánto cuesta ese despliegue a distintos volúmenes de tráfico, y en qué punto el modelo deja de tener sentido económico.

## Línea base tecnológica

| Componente | Versión | Rol |
|---|---|---|
| Java | 21 LTS (Amazon Corretto) | Runtime del lenguaje |
| Maven | 3.9.16 | Herramienta de construcción |
| Spring Boot | 4.1.1 | Framework de la aplicación |
| Docker Desktop | con Compose v2 | Contenerización y orquestación local |
| MongoDB | `mongo:8` | Servicio de base de datos del entorno multi-contenedor (Parte 3) |
| Docker Hub | `anafiquitivapoveda/virtualization-lab` | Registro de imágenes |
| Amazon Linux 2023 | en EC2 | Sistema operativo de despliegue |

## Arquitectura y diseño de clases

```
co.edu.escuelaing
├── RestServiceApplication   — punto de entrada; lee la variable PORT, default 6000
└── HelloRestController      — @RestController, GET /greeting?name=...
```

La aplicación es intencionalmente mínima —dos clases— para que la variable de estudio del taller sea el *entorno de despliegue*, no la *lógica de la aplicación*.

- **`RestServiceApplication`** fija `server.port` a partir de la variable de entorno `PORT` mediante `SpringApplication.setDefaultProperties`, usando `6000` por defecto cuando `PORT` no está definida. Esta única línea es lo que permite que el mismo artefacto compilado escuche en el puerto 6000 al ejecutarse directamente en la máquina de un desarrollador, en un puerto interno arbitrario dentro de un contenedor, y en el puerto externo que decida expone el security group de EC2 — sin cambios de código ni recompilaciones entre entornos. Esta es la demostración práctica central de la *portabilidad*: el artefacto es agnóstico al entorno por construcción.
- **`HelloRestController`** expone un único endpoint, `GET /greeting?name=<name>`, que retorna `Hello, <name>!` (por defecto `World`). Mantener la lógica de negocio trivial aísla la variable bajo estudio al mecanismo de despliegue.

> **Nota sobre el puerto (6000 vs 9000).** El enunciado del taller es internamente inconsistente: el texto pide que la aplicación use **el puerto 6000 por defecto** y verifica `http://localhost:6000/greeting`, pero el fragmento de código y el `Dockerfile` de referencia usan `9000`. Este repositorio sigue el requisito textual (**6000**) y lo aplica de forma coherente en todas las capas: default del código, `ENV PORT`/`EXPOSE` del `Dockerfile`, puerto interno de los `docker run`, de `compose.yaml` y del despliegue en EC2. Como el puerto se lee de la variable `PORT`, usar 9000 sería únicamente cuestión de pasar `-e PORT=9000 -p <host>:9000`, sin recompilar.

## Estructura del proyecto

```
pom.xml
Dockerfile
compose.yaml
.dockerignore
src/main/java/co/edu/escuelaing/RestServiceApplication.java
src/main/java/co/edu/escuelaing/HelloRestController.java
src/test/java/co/edu/escuelaing/HelloRestControllerTest.java
evidence/                  — salida de comandos, capturas y una grabación usadas como evidencia en este documento
```

## Parte 1 — Construcción y ejecución local

```bash
mvn clean package
java -jar target/virtualization-lab-1.0.0.jar
```

Verificación:

```
http://localhost:6000/greeting?name=Pedro
→ Hello, Pedro!
```

**Evidencia** — [`evidence/local_run_greeting.txt`](evidence/local_run_greeting.txt):

```
$ mvn clean package
[INFO] BUILD SUCCESS

$ java -jar target/virtualization-lab-1.0.0.jar
Tomcat started on port 6000 (http) with context path '/'
Started RestServiceApplication in 3.499 seconds

$ curl "http://localhost:6000/greeting?name=Pedro"
Hello, Pedro!
```

## Parte 2 — Contenerización y aislamiento de procesos

`Dockerfile` (single-stage, siguiendo literalmente la especificación del taller — el JAR se compila con Maven *antes* de construir la imagen, y luego se copia como artefacto estático):

```dockerfile
FROM amazoncorretto:21

WORKDIR /app

COPY target/*.jar app.jar

ENV PORT=6000

EXPOSE 6000

ENTRYPOINT ["java", "-jar", "app.jar"]
```

`amazoncorretto:21` se elige como imagen base específicamente porque solo trae la capa JRE/JDK necesaria para ejecutar el JAR — la imagen no contiene herramientas de construcción, acercando el artefacto final a lo que realmente se ejecutará en producción.

Construcción e inspección:

```bash
mvn clean package
docker build -t anafiquitivapoveda/virtualization-lab:1.0 .
docker images
```

**Evidencia** — [`evidence/docker_images.txt`](evidence/docker_images.txt):

```
REPOSITORY                              TAG       IMAGE ID       CREATED          SIZE
anafiquitivapoveda/virtualization-lab   1.0       956f1dfa72ec   56 seconds ago   771MB
anafiquitivapoveda/virtualization-lab   latest    956f1dfa72ec   56 seconds ago   771MB
```

Ejecución de un contenedor, mapeando su puerto interno a un puerto del host:

```bash
docker run -d \
  --name virtualization-lab-1 \
  -e PORT=6000 \
  -p 34000:6000 \
  anafiquitivapoveda/virtualization-lab:1.0
```

```
http://localhost:34000/greeting?name=Container
→ Hello, Container!
```

### Demostración del aislamiento entre contenedores

Se lanzan dos instancias adicionales de la *misma* imagen, cada una en su propio puerto del host:

```bash
docker run -d --name virtualization-lab-2 -p 34001:6000 anafiquitivapoveda/virtualization-lab:1.0
docker run -d --name virtualization-lab-3 -p 34002:6000 anafiquitivapoveda/virtualization-lab:1.0
```

**Evidencia** — [`evidence/docker_ps.txt`](evidence/docker_ps.txt), [`evidence/greeting_isolation_tests.txt`](evidence/greeting_isolation_tests.txt):

```
CONTAINER ID   IMAGE                                       COMMAND               STATUS          PORTS                     NAMES
53ce04698720   anafiquitivapoveda/virtualization-lab:1.0   "java -jar app.jar"   Up 15 seconds   0.0.0.0:34002->6000/tcp   virtualization-lab-3
eece73027dcb   anafiquitivapoveda/virtualization-lab:1.0   "java -jar app.jar"   Up 16 seconds   0.0.0.0:34001->6000/tcp   virtualization-lab-2
c5da97b958ee   anafiquitivapoveda/virtualization-lab:1.0   "java -jar app.jar"   Up 17 seconds   0.0.0.0:34000->6000/tcp   virtualization-lab-1

$ curl "http://localhost:34000/greeting?name=Container"
Hello, Container!
$ curl "http://localhost:34001/greeting?name=Container2"
Hello, Container2!
$ curl "http://localhost:34002/greeting?name=Container3"
Hello, Container3!
```

Confirmación visual en Docker Desktop, con los contenedores corriendo simultáneamente:

![Docker Desktop mostrando los contenedores en ejecución](evidence/docker_desktop_containers.png)

Tres contenedores, una sola imagen compartida, tres instancias de ejecución completamente independientes — cada una con su propio namespace de PID, su propio overlay de sistema de archivos y su propia pila de red, sin requerir espacio en disco adicional para el código de la aplicación más allá de las capas de la imagen, compartidas y de solo lectura. Este es el beneficio práctico de la virtualización a nivel de sistema operativo: aislamiento sin el sobrecosto de duplicar un sistema operativo invitado completo por instancia, como sí lo exigiría una VM basada en hipervisor.

## Parte 3 — Orquestación declarativa con Docker Compose

Docker Compose define y ejecuta un entorno con varios servicios relacionados. Aquí la aplicación web y MongoDB corren en **contenedores separados sobre la misma red de Docker**. La aplicación todavía no persiste datos en MongoDB (no incluye el driver); el servicio `db` existe para estudiar cómo Compose gestiona múltiples servicios, la red entre ellos, el mapeo de puertos y los volúmenes persistentes.

`compose.yaml`:

```yaml
services:
  web:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: virtualization-web
    environment:
      PORT: 6000
      SPRING_DATA_MONGODB_URI: mongodb://db:27017/workshop
    ports:
      - "8087:6000"
    depends_on:
      - db

  db:
    image: mongo:8
    container_name: virtualization-db
    volumes:
      - mongodb:/data/db
      - mongodb_config:/data/configdb
    ports:
      - "27017:27017"
    command: mongod

volumes:
  mongodb:
  mongodb_config:
```

| Elemento | Qué demuestra |
|---|---|
| `web` → `build` | Compose construye la imagen a partir del `Dockerfile` local (requiere `mvn clean package` previo). |
| `SPRING_DATA_MONGODB_URI: mongodb://db:27017/workshop` | El servicio `web` alcanza a MongoDB por el **nombre del servicio** (`db`), resuelto por el DNS interno de la red que Compose crea automáticamente. |
| `depends_on: db` | Orden de arranque: `db` se inicia antes que `web`. |
| `mongo:8` | Imagen oficial y actual de MongoDB (en lugar de la obsoleta `mongo:3.6.1`), expuesta en el puerto 27017. |
| Volúmenes `mongodb` y `mongodb_config` | Los datos viven fuera del ciclo de vida del contenedor: sobreviven a `docker compose down` y solo se borran con `down -v`. |

### Arranque y verificación

```bash
mvn clean package
docker compose up -d --build
docker compose ps
docker compose logs web
docker compose logs db
```

**Evidencia** — [`evidence/docker_compose_ps.txt`](evidence/docker_compose_ps.txt), [`evidence/docker_compose_logs.txt`](evidence/docker_compose_logs.txt), [`evidence/docker_compose_db_logs.txt`](evidence/docker_compose_db_logs.txt):

```
$ docker compose ps
NAME                 IMAGE          COMMAND                  SERVICE   STATUS          PORTS
virtualization-db    mongo:8        "docker-entrypoint.s…"   db        Up 23 seconds   0.0.0.0:27017->27017/tcp
virtualization-web   …-web          "java -jar app.jar"      web       Up 22 seconds   0.0.0.0:8087->6000/tcp

$ docker compose logs web
:: Spring Boot ::                (v4.1.1)
Tomcat started on port 6000 (http) with context path '/'
Started RestServiceApplication in 3.11 seconds

$ docker compose logs db
"msg":"MongoDB starting","attr":{"pid":1,"port":27017,"dbPath":"/data/db",...}
"msg":"Build Info","attr":{"buildInfo":{"version":"8.3.11",...}}
"msg":"Waiting for connections","attr":{"port":27017,"ssl":"off"}

$ curl "http://localhost:8087/greeting?name=Compose"
Hello, Compose!
```

### Interacción con MongoDB dentro de su contenedor

```bash
docker compose exec db mongosh
```

**Evidencia** — [`evidence/docker_compose_mongosh.txt`](evidence/docker_compose_mongosh.txt):

```
test> show dbs
admin   8.00 KiB
config 12.00 KiB
local   8.00 KiB

test> use workshop
switched to db workshop

workshop> db.messages.insertOne({ message: "Hello from Docker Compose" })
{ acknowledged: true, insertedId: ObjectId('6abaf60c827c38fa4c6e3d53') }

workshop> db.messages.find()
[ { _id: ObjectId('6abaf60c827c38fa4c6e3d53'), message: 'Hello from Docker Compose' } ]

workshop> exit
```

### Ciclo de vida: `down` vs `down -v`

```bash
docker compose down      # elimina contenedores y red, conserva los volúmenes
docker compose down -v   # elimina además los volúmenes (y los datos de MongoDB)
```

Para comprobar la persistencia se ejecutó `down`, se volvió a levantar el entorno y se consultó de nuevo la colección: el documento insertado seguía ahí, porque vive en el volumen y no en el contenedor. Solo `down -v` lo eliminó.

**Evidencia** — [`evidence/docker_compose_volumes.txt`](evidence/docker_compose_volumes.txt):

```
$ docker compose down
 Container virtualization-web  Removed
 Container virtualization-db  Removed
 Network …_default  Removed

$ docker volume ls --filter name=mongodb
local     …_mongodb
local     …_mongodb_config            ← los volúmenes sobreviven

$ docker compose up -d
$ docker compose exec db mongosh workshop --eval "db.messages.find()"
[ { _id: ObjectId('6abaf60c827c38fa4c6e3d53'), message: 'Hello from Docker Compose' } ]   ← el dato persiste

$ docker compose down -v
 Volume …_mongodb_config  Removed
 Volume …_mongodb  Removed

$ docker volume ls --filter name=mongodb
DRIVER    VOLUME NAME                  ← sin volúmenes: datos eliminados
```

Esto hace visible la separación entre **estado** y **cómputo**: el contenedor de MongoDB es desechable, mientras que los datos tienen un ciclo de vida propio en el volumen administrado por Docker.

## Parte 4 — Publicación en Docker Hub

```bash
docker login
docker tag anafiquitivapoveda/virtualization-lab:1.0 anafiquitivapoveda/virtualization-lab:latest
docker push anafiquitivapoveda/virtualization-lab:1.0
docker push anafiquitivapoveda/virtualization-lab:latest
```

**Repositorio en Docker Hub:** https://hub.docker.com/r/anafiquitivapoveda/virtualization-lab

**Evidencia** — [`evidence/docker_push_1.0.txt`](evidence/docker_push_1.0.txt), [`evidence/docker_push_latest.txt`](evidence/docker_push_latest.txt):

```
1.0: digest: sha256:c5df78188019b80f187b526141d232d7168491f0e4c88c22fb604740a703e2d8 size: 856
latest: digest: sha256:c5df78188019b80f187b526141d232d7168491f0e4c88c22fb604740a703e2d8 size: 856
```

Ambos tags publican el mismo digest de manifiesto (y localmente comparten el mismo image ID, `956f1dfa72ec`), es decir, apuntan a la misma imagen subyacente y son visibles públicamente en el repositorio de Docker Hub, confirmado a continuación:

![Repositorio de Docker Hub mostrando ambos tags publicados](evidence/dockerhub_tags.jpg)

Publicar en un registro es lo que hace que la imagen sea *portable entre hosts sin un sistema de archivos compartido* — la instancia EC2 de la Parte 5 nunca ve el código fuente ni la cadena de construcción local; solo descarga (`pull`) el artefacto final y versionado.

## Parte 5 — Despliegue en AWS EC2

Se lanzó una instancia `t3.micro` con Amazon Linux 2023.

**Evidencia** — Consola de EC2, instancia `i-0c0b40efc68f83a64` en estado `Running`, tipo `t3.micro`, región `US East (N. Virginia)`:

![Consola de AWS EC2 mostrando la instancia en ejecución](evidence/ec2_instance_running_console.png)

**Nota sobre el security group:** por restricciones de la red institucional (universidad) que bloqueaban intermitentemente el acceso, las reglas de entrada de SSH (puerto 22) y del puerto de la aplicación (8080) se dejaron temporalmente abiertas a cualquier IP (`0.0.0.0/0`) en lugar de restringirlas a una IP específica, que es lo que recomienda el taller y la buena práctica de seguridad. Esta es una desviación consciente y documentada del principio de mínima exposición, hecha únicamente para completar la verificación del despliegue durante la ventana de evaluación — **la instancia debe terminarse (o el security group debe volver a restringirse) inmediatamente después de la revisión** para no dejar un servidor SSH expuesto a internet.

**Nota de conectividad (un obstáculo real, documentado por completitud):** el cliente OpenSSH desde una terminal local agotó el tiempo de espera al conectar por el puerto 22, incluso cuando el security group todavía estaba restringido a la IP pública de la máquina. El diagnóstico (`Test-NetConnection`, verificación de resolución DNS) aisló la causa al bloqueo del puerto 22 saliente por parte de la red institucional —una restricción común en redes universitarias/corporativas—, no a una mala configuración del lado de AWS. Se usó en su lugar **AWS EC2 Instance Connect** (el cliente SSH basado en navegador, integrado en la consola de EC2), ya que su ruta de tráfico no depende de la política de puerto 22 saliente de la red local.

Instalación de Docker y ejecución de la imagen, corridas en la instancia vía EC2 Instance Connect:

```bash
sudo yum update -y
sudo yum install -y docker
sudo service docker start
sudo usermod -a -G docker ec2-user
# reconectar para que el nuevo grupo tenga efecto
docker pull anafiquitivapoveda/virtualization-lab:1.0
docker run -d \
  --name virtualization-lab \
  --restart unless-stopped \
  -e PORT=6000 \
  -p 8080:6000 \
  anafiquitivapoveda/virtualization-lab:1.0
```

**Evidencia** — [`evidence/ec2_deployment.txt`](evidence/ec2_deployment.txt):

```
$ docker ps
CONTAINER ID   IMAGE                                       COMMAND               STATUS          PORTS
7a7a660a9395   anafiquitivapoveda/virtualization-lab:1.0   "java -jar app.jar"   Up 46 seconds   0.0.0.0:8080->6000/tcp

$ docker logs virtualization-lab
:: Spring Boot ::                (v4.1.1)
Tomcat started on port 6000 (http) with context path '/'
Started RestServiceApplication in 3.402 seconds

$ curl "http://ec2-54-159-22-98.compute-1.amazonaws.com:8080/greeting?name=AWS"
Hello, AWS!
```

**URL pública de despliegue (verificada el 22/09/2026; la instancia ya está apagada, ver nota abajo):** http://ec2-54-159-22-98.compute-1.amazonaws.com:8080/greeting?name=AWS

Confirmación desde el navegador, desde una máquina cliente distinta:

![Despliegue en EC2 respondiendo en el navegador, alcanzado desde internet público](evidence/ec2_browser_screenshot.png)

> **Estado actual de la instancia:** el despliegue se verificó el **22/09/2026** (ver la captura de la consola con la instancia en estado `Running`, la captura del navegador con fecha y hora, y la salida de `docker ps` / `docker logs` / `curl` en [`evidence/ec2_deployment.txt`](evidence/ec2_deployment.txt)). Después de la verificación, **la instancia se apagó para evitar cargos**, así que la URL de arriba ya no responde. Esto sigue la propia instrucción del taller ("Terminate the EC2 instance when the workshop ends to avoid unnecessary charges"): una máquina virtual se cobra por hora aunque no reciba solicitudes, que es justamente el fenómeno que se cuantifica en la Parte 6. El despliegue se puede reproducir en pocos minutos con los comandos de esta sección, porque la imagen `anafiquitivapoveda/virtualization-lab:1.0` sigue publicada en Docker Hub. El funcionamiento en EC2 también se muestra en el [video de demostración](#video-de-demostración).

## Parte 6 — Modelo de despliegue y análisis de costos

El despliegue no es una decisión puramente técnica — cada elección arquitectónica aquí conlleva un costo recurrente correspondiente y cuantificable. Esta sección hace explícito ese costo y razona sobre dónde el modelo se sostiene y dónde se rompe.

### Modelo de despliegue

Modelo pedido por el taller:

```
Cliente
  ↓ Solicitud HTTP
Máquina virtual EC2
  ↓
Docker Engine
  ↓
Contenedor de la aplicación web Java
```

Diagrama detallado del despliegue implementado, incluyendo el security group y el flujo de la imagen desde Docker Hub:

```mermaid
flowchart TB
    client["Cliente<br/>(navegador / curl)"]
    hub[("Docker Hub<br/>anafiquitivapoveda/virtualization-lab:1.0")]
    dev["Máquina de desarrollo<br/>mvn clean package → docker build → docker push"]

    subgraph aws["AWS — us-east-1"]
        sg{{"Security group<br/>TCP 22 (SSH) · TCP 8080 (app)"}}
        subgraph ec2["EC2 t3.micro — Amazon Linux 2023"]
            engine["Docker Engine"]
            subgraph ctr["Contenedor virtualization-lab"]
                app["Spring Boot 4.1.1 / Corretto 21<br/>PORT=6000 · GET /greeting"]
            end
            ebs[("EBS gp3 8 GB")]
        end
    end

    dev -- "docker push" --> hub
    hub -- "docker pull" --> engine
    client -- "HTTP :8080/greeting?name=AWS" --> sg
    sg -- "tráfico permitido" --> engine
    engine -- "-p 8080:6000" --> app
    ec2 --- ebs
```

| Capa | Responsabilidad |
|---|---|
| Máquina virtual EC2 | Recursos aislados de cómputo, memoria, almacenamiento y red, alquilados por hora sin importar la utilización. |
| Docker Engine | Ejecuta el proceso aislado del contenedor; media su acceso al kernel del host, al namespace de red y al sistema de archivos, y publica el puerto `8080` del host hacia el `6000` del contenedor. |
| Contenedor Docker | Un entorno de ejecución portátil que empaqueta la aplicación junto con su runtime de la JVM, independiente del software que tenga instalado el host. |
| Aplicación web Java | Recibe solicitudes HTTP y provee la funcionalidad de negocio (`/greeting`). |
| Security group | La única puerta de red en este modelo — determina qué tráfico entrante puede siquiera llegar a la máquina virtual antes de que cualquiera de las capas anteriores lo vea. |
| Docker Hub | Registro desde el cual la VM obtiene la imagen versionada; desacopla dónde se construye la imagen de dónde se ejecuta. |

### Supuestos de carga de trabajo

| Escenario | Solicitudes/mes | Región | Tipo de instancia | Instancias | Tiempo mensual | EBS | Transferencia saliente (estimada) | Tamaño prom. solicitud/respuesta | ¿Continuo? | ¿Requiere alta disponibilidad? |
|---|---|---|---|---|---|---|---|---|---|---|
| Pequeño | 10.000 | us-east-1 | t3.micro | 1 | 730 h (24/7) | 8 GB gp3 + snapshots | 1 GB | ~0.5 KB / ~0.3 KB | Sí (siempre encendido, tráfico bajo) | No |
| Mediano | 100.000 | us-east-1 | t3.small | 1 | 730 h (24/7) | 8 GB gp3 | 10 GB | ~0.5 KB / ~0.3 KB | Sí | No |
| Grande | 1.000.000 | us-east-1 | t3.medium | 2 | 730 h cada una | 8 GB gp3 cada una | 100 GB | ~0.5 KB / ~0.3 KB | Sí | Sí (ver nota sobre el balanceador) |

Aclaraciones sobre los supuestos:

- **Tiempo de ejecución:** 730 h/mes es el valor que usa la Calculadora de Precios de AWS para un servicio 24/7 (8.760 h / 12). En ningún escenario se apaga la instancia fuera de horario.
- **Transferencia saliente:** a partir del tamaño de respuesta (~0.3 KB), el tráfico mínimo real sería apenas ~3 MB, ~30 MB y ~300 MB al mes. Los valores de 1 / 10 / 100 GB son una **cota superior deliberadamente conservadora**, que absorbe cabeceras HTTP, overhead de TCP/TLS, reintentos y un crecimiento futuro del tamaño de las respuestas. Aun así, la transferencia pesa poco en el total (≈ USD 0.09 / 0.90 / 9.00).
- **Snapshots de EBS:** el escenario pequeño se configuró en la calculadora como un servicio EBS independiente con **snapshots 2× diarios** (USD 5.53). En los escenarios mediano y grande el volumen de 8 GB se incluyó dentro de la línea de EC2, **sin snapshots**. Si se normaliza el escenario pequeño con el mismo criterio que los otros dos (solo 8 GB gp3 ≈ USD 0.64), su costo quedaría en **≈ USD 8.32/mes** (≈ USD 0.000832 por solicitud). La tabla de abajo conserva el valor exportado de la calculadora (USD 13.21), para que coincida con la evidencia.
- **Balanceador de carga:** el escenario grande usa 2 instancias para tener holgura y alta disponibilidad, lo que en la práctica requiere un Application Load Balancer. **El ALB no está incluido en la estimación** (solo EC2, EBS y transferencia, que son las tres dimensiones que pide el taller); agregarlo sumaría aproximadamente USD 17–23/mes (cargo horario + LCU mínimas en us-east-1).

El tamaño de instancia sigue el volumen de solicitudes en lugar de ser fijo: una `t3.micro` atiende sin dificultad 10.000 solicitudes/mes (en promedio, una solicitud cada ~4.4 minutos), mientras que el volumen sostenido del escenario grande (~23 solicitudes/minuto de forma continua, con picos mayores) justifica tanto una clase de instancia más grande como una segunda instancia para holgura y disponibilidad.

### Estimación de costos

Generada con la [Calculadora de Precios de AWS](https://calculator.aws/) (región `us-east-1`, precios On-Demand, un grupo de estimación por escenario), cubriendo cómputo EC2, almacenamiento EBS (gp3) y transferencia de datos saliente — las tres dimensiones de costo que especifica el taller.

**Link público de la estimación:** https://calculator.aws/#/estimate?id=9822927fb6b20dedfb570895585cc51afbc38fc7
*(los links públicos de AWS expiran después de un año; las exportaciones y capturas a continuación son la copia duradera de esta evidencia.)*

**Exportaciones de la calculadora:**
- PDF: [`evidence/aws_pricing_calculator_estimate.pdf`](evidence/aws_pricing_calculator_estimate.pdf)
- CSV: [`evidence/aws_pricing_calculator_estimate.csv`](evidence/aws_pricing_calculator_estimate.csv)

Desglose exportado (del CSV):

| Grupo | Servicio | Configuración | USD/mes |
|---|---|---|---|
| Pequeño | Amazon EC2 | 1 × t3.micro, On-Demand 100 %, 1 GB salida | 7.68 |
| Pequeño | Amazon EBS | 1 × 8 GB gp3, snapshots 2× diarios | 5.53 |
| Mediano | Amazon EC2 | 1 × t3.small, 8 GB EBS, 10 GB salida | 16.72 |
| Grande | Amazon EC2 | 2 × t3.medium, 8 GB EBS, 100 GB salida | 71.02 |
| **Total** | | | **100.95** |

**Recorrido (GIF):** un recorrido grabado sobre la estimación en vivo, desde el resumen total hacia cada uno de los tres grupos de escenario y de vuelta.

![Recorrido de la estimación en la Calculadora de Precios de AWS por los tres escenarios](evidence/part6_cost_analysis_walkthrough.gif)

![Resumen de la estimación en la Calculadora de Precios de AWS](evidence/aws_pricing_calculator_summary.jpg)
![Grupos de escenarios en la Calculadora de Precios de AWS](evidence/aws_pricing_calculator_groups.jpg)

### Tabla de análisis de costos

| Escenario | Solicitudes mensuales | Costo mensual de infraestructura | Costo estimado por solicitud | Principales factores de costo |
|---|---|---|---|---|
| Carga pequeña | 10.000 | USD 13.21 | USD 13.21 / 10.000 = **USD 0.001321** | Tiempo de ejecución EC2 (t3.micro, 730 h) y almacenamiento EBS con snapshots |
| Carga media | 100.000 | USD 16.72 | USD 16.72 / 100.000 = **USD 0.000167** | Tiempo de ejecución EC2 (t3.small, 730 h), almacenamiento y 10 GB de transferencia de red |
| Carga grande | 1.000.000 | USD 71.02 | USD 71.02 / 1.000.000 = **USD 0.000071** | Capacidad de instancias (2 × t3.medium), transferencia de red (100 GB) y necesidad de escalar (+ ALB no incluido) |

`Costo estimado por solicitud = costo mensual de infraestructura / solicitudes mensuales`

El costo por solicitud cae aproximadamente **19×** del escenario pequeño al grande, aunque el costo total de infraestructura solo crece ~5.4× ante un aumento de 100× en el tráfico. El mecanismo es la amortización: el costo horario fijo de EC2 es casi constante y se divide entre un número cada vez mayor de solicitudes a medida que crece el volumen, así que su porción por solicitud se reduce — hasta que el volumen obliga a una instancia más grande o adicional, lo cual reinicia ese costo fijo en una nueva base, más alta.

### Discusión arquitectónica

**¿Por qué un despliegue basado en EC2 tiene un costo mensual base incluso con pocas solicitudes?**
Porque la facturación está atada a que la instancia esté *encendida*, no al trabajo realmente realizado. El cómputo y el almacenamiento EBS se cobran por hora y por GB aprovisionado sin importar el tráfico — una `t3.micro` corriendo 730 horas al mes cuesta lo mismo si responde 10 solicitudes o 10.000. En este escenario, la transferencia (el único componente que depende del tráfico) es de centavos; más del 99 % de la factura es costo fijo.

**¿A partir de qué nivel de carga de trabajo el costo fijo se vuelve menos significativo por solicitud?**
A partir del **escenario mediano (100.000 solicitudes/mes)**. Entre el pequeño y el mediano el tráfico crece 10×, pero el costo solo pasa de USD 13.21 a USD 16.72 (+27 %), así que el costo por solicitud cae **~8×** (0.001321 → 0.000167). En el escenario grande sigue bajando (→ 0.000071, otros ~2.4×), pero con menos fuerza: para atender 1.000.000 de solicitudes hay que pasar a 2 × t3.medium, lo que sube la base fija. La conclusión es que, con una sola instancia pequeña, el costo fijo deja de dominar el costo por solicitud a partir de las ~100.000 solicitudes/mes; por encima de eso lo que manda es la *capacidad* requerida, no la amortización.

**¿Qué obligaría a pasar de una instancia EC2 a varias instancias?**
Saturación sostenida de CPU o memoria en el tamaño de instancia actual (o agotamiento de créditos de CPU de las instancias burstable `t3`); la necesidad de despliegues sin downtime y actualizaciones rodantes (imposible con exactamente una instancia sirviendo tráfico); requisitos de latencia geográfica que un despliegue de una sola región y una sola instancia no puede cumplir; o un requisito de alta disponibilidad, ya que una instancia EC2 es por definición un punto único de falla — su hardware anfitrión, su zona de disponibilidad y la instancia misma pueden fallar de forma independiente y tumbar todo el servicio.

**¿Qué servicios adicionales requeriría probablemente un despliegue en producción?**
Un balanceador de carga (ALB) para alta disponibilidad y despliegues rodantes sin downtime, junto con un Auto Scaling Group que reemplace instancias caídas; una base de datos administrada (Amazon RDS o Amazon DocumentDB / MongoDB Atlas, si se usara el MongoDB de la Parte 3) en el momento en que la aplicación necesite persistir algo; CloudWatch para monitoreo, logging y alertas (este despliegue actualmente no tiene ninguno — una falla pasaría inadvertida); copias de seguridad automatizadas (snapshots de EBS / AWS Backup); TLS con un certificado (ACM en el ALB) y un nombre de dominio (Route 53); y un registro de contenedores, ya sea un repositorio privado en ECR o continuar usando Docker Hub, para una distribución controlada de imágenes en lugar de un `docker pull` manual en cada host.

**¿Sería más rentable una implementación sin servidor para el escenario de carga pequeña?**
Muy probablemente sí — argumentado desde las características propias de la carga de trabajo y no desde una preferencia general por lo serverless. A 10.000 solicitudes/mes, la tasa implícita de solicitudes es aproximadamente una solicitud cada cuatro minutos en promedio, y cada solicitud se resuelve en milisegundos: la instancia EC2 de este escenario está inactiva, por construcción, más del 99.9 % del tiempo, y sin embargo se cobra por las 730 horas completas. Una opción serverless (por ejemplo AWS Lambda detrás de API Gateway) cobra por invocación y por milisegundo de ejecución, sin costo mientras está inactiva; con 10.000 invocaciones cortas al mes el costo queda en centavos (e incluso dentro del nivel gratuito), lo cual se ajusta directamente al patrón real de utilización en lugar de pagar por capacidad provisionada pero no usada. Hay que considerar el *cold start* de una aplicación Spring Boot en la JVM (latencia de segundos en la primera solicitud tras inactividad): si el servicio tiene un requisito estricto de latencia, eso pesa en contra. El balance se invierte a medida que crecen el volumen y la regularidad del tráfico: superado cierto umbral de throughput sostenido, el costo acumulado por invocación supera el costo amortizado de una instancia EC2 de costo fijo, que en los escenarios mediano y grande está mucho más consistentemente utilizada.

### Conclusión

EC2 es apropiado para los escenarios mediano y grande evaluados aquí: USD 16.72–71.02/mes es un costo pequeño y predecible para 100.000–1.000.000 de solicitudes, y el costo por solicitud sigue cayendo a medida que crece el tráfico, lo que indica que la instancia hace proporcionalmente más trabajo útil por cada dólar invertido (para el escenario grande, una configuración de alta disponibilidad real debe sumar el ALB). Para el escenario pequeño, USD 13.21/mes (≈ USD 8.32 sin snapshots) por solo 10.000 solicitudes refleja una instancia inactiva la abrumadora mayoría del tiempo — es el propio perfil de tráfico bajo y esporádico de esa carga de trabajo el argumento a favor de una alternativa serverless aquí, no una preferencia tecnológica general. EC2 se vuelve la opción económicamente eficiente cuando el tráfico es lo bastante constante y voluminoso para mantener la instancia ocupada de forma significativa; por debajo de ese umbral, su modelo de facturación siempre-encendido está pagando por capacidad inactiva.

## Video de demostración

📹 **Video:** https://youtu.be/k8HInEJ6VzE

Este video cubre el Repo 1 (Spring Boot). La extensión del framework tiene su propio video, enlazado en el README de ese repositorio: https://github.com/AnaFiquitiva/TDSE_Workshop-Containerizing-and-Deploying-a-Java-Web-Application_Framework-extension

El video muestra:
1. El despliegue local con Docker (`docker run` de las tres instancias aisladas y `docker compose up` con `web` + `db`), con las respuestas de `/greeting`.
2. El despliegue en AWS EC2: instancia en ejecución, `docker ps` / `docker logs` en la VM y el endpoint público respondiendo `Hello, AWS!`.

## Checklist de entregables

| Entregable exigido | Dónde está |
|---|---|
| Código fuente completo de Spring Boot | [`src/`](src/), [`pom.xml`](pom.xml) |
| `Dockerfile` y `compose.yaml` | [`Dockerfile`](Dockerfile), [`compose.yaml`](compose.yaml) |
| Instrucciones de build, ejecución, contenerización y despliegue | Partes 1–5 |
| URL del repositorio en Docker Hub | https://hub.docker.com/r/anafiquitivapoveda/virtualization-lab |
| Evidencia de ejecución local | Parte 1 |
| Evidencia de la imagen construida y de los contenedores | Partes 2 y 3 |
| Evidencia de la imagen en Docker Hub | Parte 4 |
| Evidencia del despliegue en EC2 y URL pública | Parte 5 |
| Diagrama del modelo de despliegue y análisis de costos | Parte 6 |
| Exportación PDF/CSV de la Calculadora de Precios de AWS | Parte 6 |
| Video de demostración | [Video de demostración](#video-de-demostración) |

## Índice de evidencias

Toda la salida de comandos, capturas y grabaciones referenciadas arriba viven en [`evidence/`](evidence/):

| Archivo | Parte | Contenido |
|---|---|---|
| `local_run_greeting.txt` | 1 | Ejecución local de `mvn clean package` + `java -jar` y verificación del endpoint. |
| `docker_images.txt` | 2 | Imagen construida, ambos tags. |
| `docker_ps.txt`, `greeting_isolation_tests.txt` | 2 | Tres contenedores aislados corriendo simultáneamente, cada uno respondiendo de forma independiente. |
| `docker_desktop_containers.png` | 2 | Captura de Docker Desktop mostrando los contenedores en ejecución. |
| `docker_compose_ps.txt` | 3 | `docker compose ps` con los servicios `web` y `db` en ejecución. |
| `docker_compose_logs.txt` | 3 | Logs de arranque del servicio `web` y verificación del endpoint en el puerto 8087. |
| `docker_compose_db_logs.txt` | 3 | Logs de arranque de MongoDB 8 (`Waiting for connections` en el 27017). |
| `docker_compose_mongosh.txt` | 3 | Sesión de `mongosh`: `show dbs`, `use workshop`, `insertOne`, `find`. |
| `docker_compose_volumes.txt` | 3 | `down` conserva los volúmenes y los datos; `down -v` los elimina. |
| `docker_push_1.0.txt`, `docker_push_latest.txt` | 4 | Digests de publicación en Docker Hub para ambos tags. |
| `dockerhub_tags.jpg` | 4 | Página pública del repositorio en Docker Hub, confirmando que ambos tags están publicados. |
| `ec2_instance_running_console.png` | 5 | Consola de AWS EC2 mostrando la instancia en estado `Running`. |
| `ec2_deployment.txt` | 5 | `docker ps` / `docker logs` en EC2 y un `curl` contra el endpoint público. |
| `ec2_browser_screenshot.png` | 5 | Confirmación desde el navegador del endpoint público en vivo, desde un cliente distinto. |
| `aws_pricing_calculator_estimate.pdf`, `aws_pricing_calculator_estimate.csv` | 6 | Exportación oficial de la estimación de la Calculadora de Precios de AWS. |
| `aws_pricing_calculator_summary.jpg`, `aws_pricing_calculator_groups.jpg` | 6 | Capturas de la estimación, totales y desagregado por escenario. |
| `part6_cost_analysis_walkthrough.gif` | 6 | Recorrido grabado de la estimación de costos en vivo por los tres escenarios. |

## Notas sobre la construcción

El `Dockerfile` de referencia del taller es single-stage y espera que `target/*.jar` ya exista — es decir, se espera que `mvn clean package` se ejecute previamente con una instalación local de JDK 21 y Maven. Este repositorio sigue eso literalmente: Amazon Corretto 21 y Maven se instalaron en la máquina de construcción específicamente para poder compilar el JAR antes de correr `docker build`, en lugar de mover la construcción de Maven a un Dockerfile multi-stage (una alternativa válida, pero no lo que especifica el `Dockerfile` provisto por el taller).
