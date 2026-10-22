# Core API — Spring Boot, Docker e CI/CD com GitHub Actions

Projeto de estudo para construir uma API REST com **Java 21 e Spring Boot**, empacotá-la em uma imagem Docker e automatizar o processo de build e publicação com **GitHub Actions** e **Docker Hub**, preparando a imagem para hospedagem no **Render**.

> **Sobre as imagens citadas neste README:** aqui, “imagem” significa **imagem Docker** — o pacote-base que fornece o sistema de arquivos e os componentes necessários para compilar ou executar a aplicação. O foco é explicar por que o projeto usa Alpine Linux, Ubuntu no GitHub Actions, Java 21 e as variantes JDK/JRE.

## Sumário

- [1. Objetivo](#1-objetivo)
- [2. Como a arquitetura funciona](#2-como-a-arquitetura-funciona)
- [3. Tecnologias utilizadas](#3-tecnologias-utilizadas)
- [4. Estrutura sugerida](#4-estrutura-sugerida)
- [5. Criar e validar a API](#5-criar-e-validar-a-api)
- [6. Criar a imagem Docker](#6-criar-a-imagem-docker)
- [7. Executar com Docker Compose](#7-executar-com-docker-compose)
- [8. Automatizar com GitHub Actions](#8-automatizar-com-github-actions)
- [9. Publicar o código no GitHub](#9-publicar-o-código-no-github)
- [10. Configurar os Secrets](#10-configurar-os-secrets)
- [11. Hospedar no Render](#11-hospedar-no-render)
- [12. Como validar o resultado](#12-como-validar-o-resultado)
- [13. Justificativa das imagens Docker, sistemas operacionais e versões](#13-justificativa-das-imagens-docker-sistemas-operacionais-e-versões)
- [14. Problemas comuns](#14-problemas-comuns)
- [15. Próximas melhorias](#15-próximas-melhorias)

## 1. Objetivo

O objetivo é praticar um fluxo de **CI/CD (Integração Contínua e Entrega/Implantação Contínua)**. Em vez de compilar e publicar a aplicação manualmente a cada alteração, o GitHub Actions executa uma sequência de etapas quando há um `push` na branch `main`.

Ao final do fluxo, a imagem Docker é enviada ao Docker Hub e pode ser usada por um serviço de hospedagem como o Render.

Este exemplo disponibiliza uma rota de verificação de saúde (*health check*), útil para confirmar se a API iniciou e está respondendo a requisições HTTP.

## 2. Como a arquitetura funciona

<pre>

    A[Desenvolvedor altera o código] --> B[git push na branch main]
    B --> C[GitHub Actions inicia um runner Ubuntu]
    C --> D[Checkout do repositório]
    D --> E[Configuração do Java 21 e Gradle]
    E --> F[Build da aplicação]
    F --> G[Build da imagem Docker]
    G --> H[Login no Docker Hub usando Secrets]
    H --> I[Publicação das tags latest e SHA]
    I --> J[Render utiliza a imagem publicada]
    J --> K[API disponível pela URL pública]
</pre>

### Papel de cada serviço

- **Spring Boot:** implementa a API e responde às requisições HTTP.
- **Docker:** empacota a aplicação e seu ambiente de execução em uma imagem padronizada.
- **Docker Compose:** simplifica a execução local do contêiner.
- **GitHub Actions:** executa os passos de validação, build e publicação em uma máquina virtual temporária.
- **Docker Hub:** armazena as imagens geradas; ele é um registro de imagens, não o servidor que executa a API.
- **Render:** executa a imagem publicada e disponibiliza a aplicação pela internet.

### Por que usar um Dockerfile multistage?

O primeiro estágio usa uma imagem com **JDK 21**, suficiente para compilar o código e gerar o arquivo JAR. O segundo estágio usa apenas o **JRE 21**, necessário para executar o JAR. Assim, ferramentas de compilação e arquivos de desenvolvimento não precisam fazer parte da imagem final, reduzindo seu tamanho e a quantidade de componentes presentes em produção.

> A imagem final ainda contém o que a aplicação precisa para executar. O multistage reduz a superfície da imagem, mas não substitui atualização de dependências, gestão de vulnerabilidades e configuração segura.

## 3. Tecnologias utilizadas

- Java 21
- Spring Boot e Spring Web
- Gradle Wrapper
- Docker e Docker Compose
- Git e GitHub
- GitHub Actions
- Docker Hub
- Render

## 4. Estrutura sugerida

Os nomes exatos dos pacotes podem variar conforme as opções escolhidas no Spring Initializr. Uma estrutura possível é:

```text
core-api/
├── .github/
│   └── workflows/
│       └── pipeline.yml
├── docs/
│   └── images/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/example/core_api/
│       │       ├── controller/
│       │       │   └── HealthCheckController.java
│       │       └── Payload.java
│       └── resources/
│           └── application.properties
├── .dockerignore
├── Dockerfile
├── docker-compose.yml
├── build.gradle
├── gradlew
├── gradlew.bat
├── settings.gradle
└── README.md
```

## 5. Criar e validar a API

### 5.1 Criar o projeto no Spring Initializr

Acesse [start.spring.io](https://start.spring.io/) e crie um projeto com Java 21, Gradle e Spring Web. Use o nome e o pacote que preferir; os exemplos a seguir consideram o pacote `com.example.core_api`.

Spring Web fornece os recursos necessários para criar endpoints HTTP. O Spring Boot configura boa parte da infraestrutura da aplicação, enquanto o Jackson normalmente converte objetos Java em JSON nas respostas HTTP.

### 5.2 Criar o contrato da resposta com `Payload`

Crie `src/main/java/com/example/core_api/Payload.java`:

```java
package com.example.core_api;

import java.time.Instant;

public record Payload(
    String service,
    String status,
    Instant timestamp,
    String message
) {}
```

Um `record` é adequado para transportar dados de resposta porque declara os componentes do objeto de forma concisa. Seus campos são finais e a estrutura evita escrever manualmente métodos repetitivos como getters, `equals` e `hashCode`.

- `service`: identifica o serviço que respondeu.
- `status`: informa a situação reportada pela API.
- `timestamp`: registra o instante em que a resposta foi montada.
- `message`: apresenta uma mensagem legível.

### 5.3 Criar o controlador de saúde

Crie `src/main/java/com/example/core_api/controller/HealthCheckController.java`:

```java
package com.example.core_api.controller;

import com.example.core_api.Payload;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthCheckController {

    @GetMapping
    public ResponseEntity<Payload> status() {
        Payload payload = new Payload(
            "core-api",
            "UP",
            Instant.now(),
            "CI/CD pipeline successfully executed"
        );

        return ResponseEntity.ok(payload);
    }
}
```

**Como funciona:**

1. `@RestController` informa ao Spring que a classe trata requisições REST e que os retornos devem ser enviados no corpo da resposta, normalmente serializados em JSON.
2. `@RequestMapping("/api/health")` define o caminho-base do controlador.
3. `@GetMapping` vincula o método às requisições HTTP GET nesse caminho.
4. `Instant.now()` captura o instante do sistema quando o método é executado.
5. `ResponseEntity.ok(payload)` devolve o código HTTP `200 OK` junto do objeto `Payload`.

Esse endpoint ajuda a confirmar que a aplicação responde. Em ambientes com orquestradores, verificações de saúde podem ser configuradas para acompanhar aplicações, mas este endpoint, sozinho, não configura nem reinicia contêineres ou um cluster Kubernetes.

### 5.4 Executar localmente

No Windows, abra o terminal na pasta raiz do projeto e execute:

```powershell
.\gradlew.bat bootRun
```

No Linux ou macOS:

```bash
chmod +x gradlew
./gradlew bootRun
```

A aplicação deve iniciar na porta `8080`, desde que essa porta esteja disponível. Acesse:

- Local: `http://localhost:8080/api/health`

A resposta esperada é um JSON semelhante a este — o valor de `timestamp` será diferente a cada chamada:

```json
{
  "service": "core-api",
  "status": "UP",
  "timestamp": "2026-10-16T23:59:00Z",
  "message": "CI/CD pipeline successfully executed"
}
```

## 6. Criar a imagem Docker

Na raiz do projeto, crie um arquivo chamado `Dockerfile` (sem extensão).

Para que a cópia do artefato seja previsível, configure o Gradle para gerar o JAR executável com o nome `app.jar`. No `build.gradle` em formato Groovy, adicione:

```groovy
tasks.named('bootJar') {
    archiveFileName = 'app.jar'
}
```

Se o projeto usar `build.gradle.kts`, a sintaxe será diferente; adapte o trecho à configuração Kotlin DSL.

Use este `Dockerfile`:

```dockerfile
# Estágio 1: compilar o projeto com o JDK 21
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY . .
RUN chmod +x gradlew && ./gradlew clean bootJar -x test

# Estágio 2: executar a aplicação com um runtime mais enxuto
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Entendendo as instruções

- `FROM ... AS builder`: inicia o estágio de compilação, com JDK.
- `WORKDIR /app`: define a pasta de trabalho dentro da imagem.
- `COPY . .`: copia o contexto do projeto para o estágio.
- `./gradlew clean bootJar -x test`: limpa saídas anteriores e gera o JAR executável; `-x test` **pula a execução dos testes**.
- Segundo `FROM`: cria o estágio final baseado em JRE, sem carregar o JDK do estágio anterior.
- `COPY --from=builder ...`: copia apenas o JAR produzido.
- `EXPOSE 8080`: documenta a porta usada pela aplicação; não publica a porta sozinho.
- `ENTRYPOINT`: define o comando executado ao iniciar o contêiner.

**Observação sobre os testes:** `-x test` pode acelerar o build, mas não valida os testes automatizados. Para uma esteira mais robusta, é preferível executar os testes em uma etapa separada e só publicar a imagem se eles passarem.

### Arquivo `.dockerignore`

Crie também `.dockerignore` para evitar copiar arquivos desnecessários para o contexto enviado ao Docker:

```text
.git
.gradle
build
.idea
.vscode
*.iml
```

## 7. Executar com Docker Compose

Na raiz do projeto, crie `docker-compose.yml`:

```yaml
services:
  backend:
    build:
      context: .
    container_name: core_api
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: production
```

A configuração acima constrói a imagem usando o `Dockerfile` da raiz, nomeia o contêiner `core_api`, mapeia a porta `8080` do computador para a porta `8080` do contêiner e define o perfil Spring como `production`.

> Ative `SPRING_PROFILES_ACTIVE=production` se esse perfil fizer parte da configuração do projeto. Caso exista um `application-production.properties` ou `application-production.yml`, revise-o para garantir que contenha as configurações esperadas para produção.

Execute:

```bash
docker compose up --build
```

Para executar em segundo plano:

```bash
docker compose up --build -d
```

Para conferir os contêineres e os logs:

```bash
docker compose ps
docker compose logs -f backend
```

Para encerrar os serviços iniciados pelo Compose:

```bash
docker compose down
```

Depois de iniciar, teste `http://localhost:8080/api/health`.

## 8. Automatizar com GitHub Actions

Crie a estrutura `.github/workflows/` e, dentro dela, o arquivo `pipeline.yml`.

```yaml
name: core-api-pipeline

on:
  push:
    branches:
      - main

permissions:
  contents: read

jobs:
  build-and-push:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout source code
        uses: actions/checkout@v4

      - name: Configurar Java 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: gradle

      - name: Dar permissão ao Gradle Wrapper
        run: chmod +x gradlew

      - name: Build da aplicação
        run: ./gradlew clean build -x test

      - name: Configurar Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Login no Docker Hub
        uses: docker/login-action@v3
        with:
          username: ${{ secrets.DOCKER_USERNAME }}
          password: ${{ secrets.DOCKER_TOKEN }}

      - name: Construir e publicar a imagem
        uses: docker/build-push-action@v5
        with:
          context: .
          push: true
          tags: |
            ${{ secrets.DOCKER_USERNAME }}/core-api:latest
            ${{ secrets.DOCKER_USERNAME }}/core-api:${{ github.sha }}
```

### O que acontece em cada etapa?

1. **Gatilho `push` na `main`:** inicia a execução quando o código é enviado à branch principal.
2. **Checkout:** baixa uma cópia do repositório para o runner temporário do GitHub.
3. **Setup Java:** prepara o Java 21 e ativa o cache de dependências do Gradle para execuções futuras.
4. **Permissão do `gradlew`:** permite executar o wrapper no Linux.
5. **Build Gradle:** verifica se o projeto compila. Neste exemplo, `-x test` faz com que os testes sejam ignorados.
6. **Buildx e login:** preparam o ambiente de build de imagem e autenticam no Docker Hub com secrets.
7. **Build e push:** constrói a imagem e publica duas tags no registro.

O Gradle é executado diretamente no workflow e também no estágio `builder` do Dockerfile. Isso significa que o código é compilado duas vezes. É uma escolha didática que valida a compilação antes do build da imagem, mas pode ser otimizada em uma versão futura.

### Por que duas tags?

- `latest`: aponta para a versão que foi publicada mais recentemente com essa tag. Como ela pode ser atualizada, não identifica sozinha uma versão histórica imutável.
- `${{ github.sha }}`: identifica a imagem com o hash do commit que disparou o workflow. Isso facilita rastrear a origem da imagem e selecionar uma versão específica para um rollback.

## 9. Publicar o código no GitHub

Crie um repositório no GitHub. Se ainda não inicializou o Git na pasta local, execute na raiz do projeto:

```bash
git init
git branch -M main
git add .
git commit -m "initial commit"
git remote add origin git@github.com:SEU-USUARIO/SEU-REPOSITORIO.git
git push -u origin main
```

Troque a URL de exemplo pela URL real do seu repositório. Se o Git já estiver configurado, não repita `git init` ou `git remote add origin`; verifique primeiro com `git status` e `git remote -v`.

Após o `push` para `main`, abra a aba **Actions** do GitHub e acompanhe a execução de `core-api-pipeline`.

## 10. Configurar os Secrets

A pipeline depende de credenciais guardadas de forma segura no GitHub; não coloque tokens diretamente no YAML ou no código-fonte.

1. No Docker Hub, crie um repositório chamado `core-api` e gere um token de acesso com as permissões necessárias para publicar imagens.
2. No repositório do GitHub, acesse **Settings → Secrets and variables → Actions**.
3. Crie os seguintes repository secrets, respeitando os nomes usados no YAML:
   - `DOCKER_USERNAME`: seu nome de usuário no Docker Hub.
   - `DOCKER_TOKEN`: token de acesso do Docker Hub.
4. Salve os secrets e execute novamente a pipeline por meio de um novo `push` para a branch `main`.

## 11. Hospedar no Render

Depois que a pipeline publicar a imagem no Docker Hub:

1. Entre em [render.com](https://render.com/) e crie um serviço do tipo **Web Service**.
2. Escolha a opção para usar uma **imagem Docker existente** (*Existing Image*), em vez de conectar o código-fonte para compilar novamente.
3. Informe o endereço da imagem do Docker Hub, por exemplo `docker.io/SEU-USUARIO/core-api:latest`.
4. Revise as configurações de porta e de implantação, garantindo que o serviço consiga encaminhar as requisições à aplicação na porta `8080`.
5. Faça o deploy e confira os logs do serviço. Configure ou acione uma nova implantação após a publicação de imagens futuras, de acordo com as opções de atualização apresentadas pelo Render.

O endereço registrado no material enviado como exemplo de implantação é:

`https://core-api-latest-7se1.onrender.com`

A URL pode variar conforme o serviço criado e pode não estar disponível se o serviço tiver sido removido, suspenso ou reconfigurado. Depois do deploy, teste a rota completa acrescentando `/api/health` ao domínio.

## 12. Como validar o resultado

Use esta lista para conferir o fluxo completo:

- [ ] A API inicia localmente com Java/Gradle.
- [ ] `GET /api/health` responde com `200 OK` e um JSON com os campos esperados.
- [ ] `docker compose up --build` inicia o contêiner sem erros.
- [ ] O endpoint também responde em `http://localhost:8080/api/health` quando executado via Docker.
- [ ] O workflow do GitHub Actions conclui todas as etapas.
- [ ] O Docker Hub contém as tags `latest` e o hash do commit.
- [ ] O Render executa a imagem publicada e o endpoint público responde.

Lembre-se: a configuração demonstrada usa `-x test`; por isso, uma pipeline verde indica que as etapas configuradas passaram, mas **não** prova que testes unitários foram executados.

## 13. Justificativa das imagens Docker, sistemas operacionais e versões

Esta seção explica as escolhas feitas no `Dockerfile` e no workflow. É importante separar dois conceitos: **Ubuntu é o sistema operacional da máquina virtual que executa o GitHub Actions**, enquanto **Alpine é o sistema-base usado dentro das imagens Docker da aplicação**. Eles ocupam camadas diferentes do processo e não são alternativas concorrentes.

### 13.1 Java 21: manter a versão consistente

O projeto usa Java 21 no Spring Initializr, no ambiente de build da pipeline e nas imagens Docker. Manter a mesma versão principal nas três etapas diminui o risco de compilar com uma versão e executar com outra que tenha diferenças de compatibilidade.

- No Spring Initializr, Java 21 define a versão usada para desenvolver e compilar o código.
- Em `actions/setup-java`, `java-version: '21'` prepara o JDK 21 no runner do GitHub Actions.
- Em `eclipse-temurin:21-...`, o `21` seleciona a linha de imagens preparada para Java 21.

O número `21` identifica a versão principal, mas **não fixa uma atualização específica do Java**. A imagem pode receber atualizações de manutenção dentro dessa linha. Para builds estritamente reproduzíveis, fixe também a versão de atualização da imagem ou seu digest e atualize essa referência de maneira controlada.

### 13.2 Eclipse Temurin: a distribuição do Java

`eclipse-temurin` é a distribuição OpenJDK fornecida pelo projeto Eclipse Adoptium e disponibilizada em imagens oficiais. Neste projeto, ela fornece o Java para os dois estágios do Dockerfile.

A tag tem a seguinte lógica:

```text
eclipse-temurin:<versão-java>-<tipo-de-runtime>-<sistema-base>
```

Por exemplo, `eclipse-temurin:21-jdk-alpine` significa Java 21, com o kit de desenvolvimento JDK, sobre Alpine Linux. Já `eclipse-temurin:21-jre-alpine` significa Java 21, com o ambiente de execução JRE, também sobre Alpine Linux. A nomenclatura e as variantes são documentadas pelo [Eclipse Adoptium](https://adoptium.net/pt-BR/installation/containers).

### 13.3 Por que Alpine Linux?

As duas linhas do Dockerfile usam Alpine:

```dockerfile
FROM eclipse-temurin:21-jdk-alpine AS builder
# ...compilação...
FROM eclipse-temurin:21-jre-alpine
```

Alpine é uma distribuição Linux conhecida por sua proposta enxuta. Ela foi escolhida para partir de uma base leve e manter reduzido o ambiente dentro do contêiner. No estágio final, essa característica combina com a decisão de usar JRE em vez de JDK.

**Vantagens esperadas:**

- menor quantidade de componentes do sistema-base em comparação a imagens mais completas;
- imagem potencialmente menor para armazenar e transferir;
- ambiente de execução mais enxuto quando combinado com multistage e JRE.

**Pontos de atenção:**

- uma imagem Alpine não é automaticamente mais segura; ainda é necessário atualizar, analisar vulnerabilidades e configurar permissões corretamente;
- Alpine usa a biblioteca C `musl`, enquanto diversas distribuições Linux tradicionais usam `glibc`. Dependências nativas ou ferramentas auxiliares podem ter diferenças de compatibilidade;
- o tamanho final depende das camadas, dependências e conteúdo da aplicação; por isso, deve ser medido, não presumido.

Para esta API Spring Boot simples, Alpine é uma opção razoável desde que a imagem seja testada no build e no ambiente de hospedagem. Para aplicações com dependências nativas incompatíveis, uma variante Temurin baseada em Ubuntu pode ser mais simples de operar, mesmo que resulte em uma imagem maior. A documentação oficial do Adoptium descreve Alpine como uma variante disponível para imagens leves, mas recomenda a imagem padrão baseada em Ubuntu para a maioria dos usuários: [imagens de contêiner Temurin](https://adoptium.net/pt-BR/installation/containers).

### 13.4 Por que JDK no build e JRE na execução?

O projeto usa **JDK** no primeiro estágio porque precisa das ferramentas necessárias para compilar o código Java e gerar o JAR. Depois usa **JRE** no estágio final porque o objetivo ali é executar o JAR já pronto.

| Estágio | Imagem utilizada | Motivo |
|---|---|---|
| `builder` | `eclipse-temurin:21-jdk-alpine` | Inclui ferramentas de desenvolvimento necessárias para a compilação com Gradle. |
| Imagem final | `eclipse-temurin:21-jre-alpine` | Contém o ambiente de execução Java necessário para iniciar a aplicação, sem carregar o JDK completo. |

O `COPY --from=builder` transporta o JAR entre os estágios, e não o ambiente de build inteiro. Essa é a finalidade do **multistage build**: separar as ferramentas usadas para construir a aplicação dos componentes necessários para executá-la. A documentação do Docker explica que isso evita colocar ferramentas e arquivos de compilação desnecessários na imagem final e pode reduzir o tamanho e a superfície de ataque ([Docker — Multi-stage builds](https://docs.docker.com/get-started/docker-concepts/building-images/multi-stage-builds/)). Isso não elimina a necessidade de corrigir vulnerabilidades ou proteger a aplicação.

### 13.5 Por que Ubuntu no GitHub Actions?

O workflow define:

```yaml
runs-on: ubuntu-latest
```

Essa configuração solicita ao GitHub Actions uma máquina virtual hospedada pelo GitHub com a imagem de runner identificada como `ubuntu-latest`. O runner é o ambiente temporário que clona o repositório, instala/configura Java, executa comandos e cria a imagem Docker. Ubuntu foi escolhido por oferecer um ambiente Linux compatível com o Gradle Wrapper, Docker e o comando `chmod +x gradlew` utilizado na esteira.

A escolha **não significa que o Windows não possa executar CI/CD**. Há runners Windows disponíveis; neste projeto, Linux foi escolhido por conveniência e por se alinhar ao uso do Dockerfile baseado em Linux.

A etiqueta `ubuntu-latest` é móvel: o GitHub pode atualizá-la para outra versão suportada do Ubuntu. Na consulta da documentação dos runners em 22 de outubro de 2026, `ubuntu-latest` corresponde a Ubuntu 24.04. Para tornar o sistema operacional do runner mais explícito, pode-se usar `ubuntu-24.04`, aceitando que essa versão também precisará ser atualizada quando seu ciclo de suporte terminar. Consulte a [documentação oficial de runners do GitHub Actions](https://docs.github.com/en/actions/how-tos/write-workflows/choose-where-workflows-run/choose-the-runner-for-a-job).

### 13.6 Versões das Actions do workflow não são versões do Java

O workflow contém referências como `actions/checkout@v4`, `actions/setup-java@v4`, `docker/setup-buildx-action@v3`, `docker/login-action@v3` e `docker/build-push-action@v5`. O número depois de `@` identifica a versão principal da **Action**, não a versão do Java, Ubuntu ou Docker.

| Configuração | O que seleciona | Por que está no projeto |
|---|---|---|
| `runs-on: ubuntu-latest` | Imagem da máquina virtual do runner | Define o sistema operacional no qual os comandos da pipeline rodam. |
| `actions/checkout@v4` | Action de checkout | Baixa o código do repositório para o runner. |
| `actions/setup-java@v4` + `java-version: '21'` | Action e versão do Java separadamente | Instala/configura Java 21 e o cache do Gradle. |
| `eclipse-temurin:21-jdk-alpine` | Imagem do estágio de build | Fornece JDK 21 sobre Alpine para gerar o artefato. |
| `eclipse-temurin:21-jre-alpine` | Imagem do estágio final | Fornece runtime Java 21 sobre Alpine para executar o JAR. |
| `docker/setup-buildx-action@v3` | Action para configurar Buildx | Prepara o builder usado pelo processo de construção da imagem. |
| `docker/login-action@v3` | Action de autenticação | Faz login no Docker Hub usando os Secrets do GitHub. |
| `docker/build-push-action@v5` | Action de build e publicação | Constrói a imagem Docker e envia as tags ao Docker Hub. |

As versões de Actions mostradas são as que este exemplo utiliza; não são uma afirmação de que sejam sempre as versões mais recentes disponíveis. Projetos com requisitos fortes de segurança da cadeia de fornecimento podem fixar Actions por SHA completo de commit e revisar atualizações deliberadamente, seguindo as orientações de segurança e manutenção do GitHub.

### 13.7 Tags móveis versus versões fixadas

O projeto usa tags convenientes, como `21-jdk-alpine`, `21-jre-alpine` e `latest`. Elas simplificam o aprendizado, mas não representam necessariamente uma versão imutável: o conteúdo associado a uma tag pode ser atualizado ao longo do tempo.

- **Para estudar e acompanhar atualizações:** tags de versão principal são simples de manter, mas um build futuro pode baixar uma imagem diferente.
- **Para maior reprodutibilidade:** fixe a versão de manutenção e a variante do Alpine — ou o digest da imagem — e atualize-as de forma explícita.
- **Para segurança:** não basta fixar uma imagem uma única vez. A base precisa receber atualizações de segurança e o projeto deve reconstruir e publicar imagens atualizadas.

Há um equilíbrio entre reprodutibilidade e atualização. Fixar tudo ajuda a reconstruir a mesma imagem, enquanto acompanhar tags móveis pode absorver atualizações automaticamente, mas diminui a previsibilidade. Para um ambiente de produção, escolha uma política deliberada de atualização e teste antes de promover novas imagens.

### Fontes oficiais para as escolhas de imagem e runner

- [Eclipse Adoptium — Imagens de contêiner](https://adoptium.net/pt-BR/installation/containers): explica as variantes Temurin, os nomes das tags e os sistemas-base disponíveis.
- [Docker — Multi-stage builds](https://docs.docker.com/get-started/docker-concepts/building-images/multi-stage-builds/): explica a separação entre estágio de build e imagem final.
- [GitHub — Escolher um runner para um job](https://docs.github.com/en/actions/how-tos/write-workflows/choose-where-workflows-run/choose-the-runner-for-a-job): descreve as etiquetas e imagens de runner disponibilizadas pelo GitHub.

## 14. Problemas comuns

### `gradlew` não tem permissão de execução no Linux

Execute `chmod +x gradlew`. A pipeline já inclui essa etapa. No Windows, use `gradlew.bat` para rodar comandos Gradle diretamente no terminal local.

### O Docker não encontra o JAR

Confira se `build.gradle` define `archiveFileName = 'app.jar'` para a tarefa `bootJar` e se o estágio builder termina sem erro. O caminho usado no Dockerfile precisa corresponder exatamente ao nome gerado em `build/libs/`.

### A porta 8080 já está em uso

Encerre o processo que usa a porta ou altere a porta do host no Compose, por exemplo `8081:8080`; nesse caso, acesse `http://localhost:8081/api/health`.

### Falha no login ou no push para o Docker Hub

Confira se `DOCKER_USERNAME` e `DOCKER_TOKEN` existem como repository secrets, se o token ainda é válido, se possui permissão de escrita e se o repositório de destino está correto.

### O workflow não executa

Confira se o arquivo está exatamente em `.github/workflows/pipeline.yml`, se o commit foi enviado para a branch `main` e se o YAML está com a indentação correta.

### O Render não responde

Consulte os logs de implantação, verifique se a imagem é pública ou se a autenticação está configurada quando necessária, confirme a porta de escuta e teste a rota `/api/health`. A URL de exemplo pode não corresponder ao serviço que você criou.

## 15. Próximas melhorias

- Executar testes unitários e de integração antes de publicar a imagem, removendo o `-x test` quando a suíte estiver pronta.
- Adicionar análise de dependências e de vulnerabilidades da imagem.
- Configurar health checks e monitoramento de disponibilidade no serviço de hospedagem.
- Adicionar estratégia de rollback usando a tag do commit.
- Considerar aprovação manual ou ambientes separados antes de promover uma versão para produção.

---

**Resumo:** o projeto demonstra o caminho do código Java até uma API hospedada: Spring Boot implementa o endpoint, Docker empacota a aplicação, GitHub Actions automatiza build e publicação, Docker Hub armazena as imagens e Render executa a imagem para disponibilizar o serviço pela internet.
