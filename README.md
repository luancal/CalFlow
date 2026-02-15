# 👑  - Sistema Inteligente de Agendamento via WhatsApp

![Java](https://img.shields.io/badge/Java-17-orange?style=flat&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green?style=flat&logo=springboot)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue?style=flat&logo=postgresql)
![Docker](https://img.shields.io/badge/Docker-Compose-blue?style=flat&logo=docker)
![Evolution API](https://img.shields.io/badge/Evolution%20API-v2-purple?style=flat)
![License](https://img.shields.io/badge/License-Proprietary-red?style=flat)

> Sistema automatizado de agendamento com IA conversacional integrado ao WhatsApp Business, Google Calendar e Evolution API v2.

---

## 📋 **Índice**

- [Sobre o Projeto](#-sobre-o-projeto)
- [Funcionalidades](#-funcionalidades)
- [Arquitetura](#-arquitetura)
- [Tecnologias](#-tecnologias)
- [Pré-requisitos](#-pré-requisitos)
- [Instalação](#-instalação)
- [Configuração](#-configuração)
- [Uso](#-uso)
- [API Documentation](#-api-documentation)
- [Deploy em Produção](#-deploy-em-produção)
- [Roadmap](#-roadmap)
- [Licença](#-licença)

---

## 🎯 **Sobre o Projeto**

O **CalFlow** é uma solução SaaS B2B para automatização de agendamentos via WhatsApp, desenvolvida para clínicas, consultórios, barbearias e prestadores de serviços agendáveis.

### **Problema Resolvido**
- ❌ Recepcionistas sobrecarregadas com ligações
- ❌ Agendamentos manuais propensos a erros
- ❌ Falta de confirmação automática de consultas
- ❌ Perda de clientes por indisponibilidade 24/7

### **Solução**
- ✅ Atendimento automatizado 24/7 via WhatsApp
- ✅ Integração nativa com Google Calendar
- ✅ Lembretes automáticos 1h antes da consulta
- ✅ Cancelamento e remarcação self-service
- ✅ Multi-instância (suporta N clientes em 1 servidor)

---

## ✨ **Funcionalidades**

### **🤖 Chatbot Conversacional**
- Máquina de estados robusta (11 estados)
- Interpretação de datas naturais: "amanhã", "segunda", "20/02"
- Escolha de serviços com preços dinâmicos
- Paginação de horários (10 por página)
- Modo "#assumir" para transferir para atendente humano

### **📅 Gestão de Agendamentos**
- Verificação automática de disponibilidade no Google Calendar
- Validação de horário de funcionamento, almoço e folgas
- Criação de eventos com metadados customizados
- Cancelamento e remarcação via WhatsApp
- Busca de agendamentos por telefone

### **⏰ Sistema de Lembretes**
- Varredura automática a cada 10 minutos
- Janela de envio: 50-70 min antes da consulta
- Cache anti-duplicidade (evita múltiplos lembretes)
- Template personalizável por clínica
- Normalização inteligente de telefones brasileiros

### **🏢 Multi-Tenancy**
- Suporte a múltiplas clínicas/instâncias
- Isolamento de dados por `clinicaId`
- Configurações independentes (horários, serviços, folgas)
- Bot ativável/desativável por clínica

---

## 🏗️ **Arquitetura**

```
┌─────────────────────────────────────────────────────────────┐
│                        USUÁRIO FINAL                         │
│                      (Cliente WhatsApp)                      │
└────────────────┬────────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────────┐
│                     EVOLUTION API v2                         │
│              (WhatsApp Business Gateway)                     │
│                    Port: 8080 (HTTP)                         │
└────────────────┬────────────────────────────────────────────┘
                 │ Webhook (messages.upsert)
                 ▼
┌─────────────────────────────────────────────────────────────┐
│                    CalFlow (Spring Boot)                    │
│                                                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐       │
│  │  WhatsApp    │  │   Event      │  │  Lembrete    │       │
│  │  Service     │──│   Service    │──│   Service    │       │
│  │ (Estado FSM) │  │ (Calendar)   │  │ (Scheduler)  │       │
│  └──────────────┘  └──────────────┘  └──────────────┘       │
│                                                             │
└────┬──────────────────────────┬────────────────────────┬────┘
     │                          │                        │
     ▼                          ▼                        ▼
┌──────────┐            ┌──────────────┐        ┌──────────────┐
│PostgreSQL│            │GoogleCalendar│        │  Redis Cache │
│  (Data)  │            │   (Eventos)  │        │  (Sessões)   │
└──────────┘            └──────────────┘        └──────────────┘
```

---

## 🛠️ **Tecnologias**

### **Backend**
- **Java 17** (OpenJDK)
- **Spring Boot 3.2.x**
    - Spring Web
    - Spring Data JPA
    - Spring Scheduling
- **Gradle 8.5** (Build Tool)

### **Infraestrutura**
- **Docker & Docker Compose**
- **PostgreSQL 15** (Banco de dados principal)
- **Redis 7** (Cache e sessões)
- **Evolution API v2** (WhatsApp Gateway)

### **Integrações**
- **Google Calendar API** (Gerenciamento de eventos)
- **Evolution API v2** (Envio de mensagens WhatsApp)

### **DevOps**
- **Caddy Server** (Reverse Proxy + SSL automático)
- **GitHub** (Versionamento)
- **Hetzner Cloud** (VPS recomendada)

---

## 📦 **Pré-requisitos**

- **Java 17+** ([Download](https://adoptium.net/))
- **Docker 24+** ([Download](https://docs.docker.com/get-docker/))
- **Docker Compose 2.20+**
- **Conta Google Cloud** (para Calendar API)
- **Domínio próprio** (recomendado para produção)

---

## 🚀 **Instalação**

### **1. Clone o Repositório**

```bash
git clone https://github.com/luancal/calflow.git
cd calflow
```

### **2. Configure o Google Calendar**

1. Acesse [Google Cloud Console](https://console.cloud.google.com/)
2. Crie um novo projeto
3. Ative a **Google Calendar API**
4. Crie credenciais:
    - Tipo: **Service Account**
    - Baixe o JSON (ex: `service-account-key.json`)
5. Compartilhe a agenda Google com o e-mail do Service Account

### **3. Configure Variáveis de Ambiente**

**Desenvolvimento (`.env`):**
```env
SERVER_URL=http://localhost:8080
AUTHENTICATION_API_KEY=SuaChaveDeDesenvolvimento123
POSTGRES_PASSWORD=postgres
DATABASE_CONNECTION_URI=postgresql://postgres:postgres@postgres:5432/calflow?schema=public
CACHE_REDIS_URI=redis://redis:6379/0
```

**Produção (`.env.production`):**
```env
SERVER_URL=https://api.seudominio.com
AUTHENTICATION_API_KEY=<GERE_SENHA_FORTE_64_CARACTERES>
POSTGRES_PASSWORD=<GERE_SENHA_FORTE_32_CARACTERES>
DATABASE_CONNECTION_URI=postgresql://postgres:${POSTGRES_PASSWORD}@postgres:5432/calflow?schema=public
CACHE_REDIS_ENABLED=true
CACHE_REDIS_URI=redis://redis:6379/0
```

**Gerar senhas fortes:**
```bash
openssl rand -hex 32  # API Key (64 chars)
openssl rand -base64 32  # Postgres Password
```

### **4. Coloque o Service Account JSON**

```bash
# Cole o arquivo na raiz do projeto:
cp /caminho/do/service-account-key.json ./service-account-key.json
```

### **5. Suba a Stack Local**

```bash
# Build + Start
docker compose up -d --build

# Verifique os logs
docker compose logs -f
```

**Acesse:**
- Evolution API: http://localhost:8080
- pgAdmin: http://localhost:5050 (admin@admin.com / admin)
- Bot Webhook: http://localhost:8081/webhook

---

## ⚙️ **Configuração**

### **1. Criar Instância WhatsApp (Evolution API)**

```bash
curl -X POST http://localhost:8080/instance/create \
  -H "apikey: SuaChaveDeDesenvolvimento123" \
  -H "Content-Type: application/json" \
  -d '{
    "instanceName": "clinica_exemplo",
    "token": "ChaveUnicaDaClinica123",
    "qrcode": true,
    "webhook": {
      "url": "http://calflow:8081/webhook",
      "events": {
        "MESSAGES_UPSERT": true,
        "CONNECTION_UPDATE": true
      }
    }
  }'
```

### **2. Escanear QR Code**

```bash
# Busque o QR Code
curl http://localhost:8080/instance/connect/clinica_exemplo \
  -H "apikey: SuaChaveDeDesenvolvimento123"

# Ou acesse: http://localhost:8080/manager
```

### **3. Cadastrar Clínica no Banco**

```sql
INSERT INTO clinica (
  nome, 
  telefone_dono, 
  nome_instancia, 
  apikey_evolution, 
  google_calendar_id,
  endereco,
  horario_abertura,
  horario_fechamento,
  intervalo_padrao,
  trabalha_sabado,
  trabalha_domingo,
  lembrete_ativo,
  bot_ativo
) VALUES (
  'Clínica Exemplo',
  '5511999999999',
  'clinica_exemplo',
  'ChaveUnicaDaClinica123',
  'seuemail@gmail.com',  -- E-mail da agenda Google
  'Rua das Flores, 123',
  8,   -- Abre às 8h
  18,  -- Fecha às 18h
  30,  -- Slots de 30 min
  true,
  false,
  true,
  true
);
```

### **4. Cadastrar Serviços**

```sql
INSERT INTO tipo_servico (nome, preco, duracao_minutos, clinica_id)
VALUES 
  ('Consulta', 150.00, 30, 1),
  ('Retorno', 80.00, 20, 1),
  ('Exame', 200.00, 45, 1);
```

---

## 💬 **Uso**

### **Fluxo de Agendamento (Cliente)**

1. Cliente envia mensagem para o WhatsApp
2. Bot pede nome (se for primeira vez)
3. Exibe menu: Agendar | Meus Agendamentos | Informações | Falar com Recepção
4. Cliente escolhe serviço (se houver múltiplos)
5. Cliente digita data ("amanhã", "quinta", "20/02")
6. Bot exibe horários disponíveis (paginados)
7. Cliente escolhe horário (ex: "3")
8. Bot confirma agendamento e cria evento no Google Calendar

### **Exemplo de Conversa**

```
👤 Cliente: Oi
🤖 Bot: Olá! Sou o assistente virtual da Clínica Exemplo. 🤝
       Para facilitar, qual seu nome completo?

👤 Cliente: João Silva
🤖 Bot: Obrigado, João Silva! Como posso te ajudar hoje?
       1. ✅ Agendar consulta
       2. 🔄 Meus Agendamentos
       3. 📞 Informações e Preços
       4. 💬 Falar com Recepção

👤 Cliente: 1
🤖 Bot: Digite o número do serviço desejado:
       1. *Consulta* - R$ 150
       2. *Retorno* - R$ 80
       3. *Exame* - R$ 200

👤 Cliente: 1
🤖 Bot: Você escolheu: Consulta.
       Agora, digite a data desejada (Ex: 20/01 ou Terça):

👤 Cliente: amanhã
🤖 Bot: Horários para *11/02*:
       1. 08:00 - 08:30
       2. 08:30 - 09:00
       3. 09:00 - 09:30
       [...]
       Digite o NÚMERO do horário (Ex: 3)
       Digite + para mais horários.

👤 Cliente: 2
🤖 Bot: ✅ Agendamento Confirmado!
       📅 Data: 11/02/2026 (terça-feira)
       🕐 Horário: 08:30
       ⏱️ Duração: 30 minutos
       Nos vemos em breve! 👍
```

---

## 📊 **API Documentation**

### **Webhook Endpoint**

**POST** `/webhook`

Recebe webhooks da Evolution API.

**Headers:**
```
Content-Type: application/json
```

**Body:**
```json
{
  "event": "messages.upsert",
  "instance": "clinica_exemplo",
  "data": {
    "key": {
      "remoteJid": "5511999999999@s.whatsapp.net",
      "fromMe": false,
      "id": "ABC123"
    },
    "message": {
      "conversation": "Olá"
    },
    "messageTimestamp": 1707588000
  }
}
```

**Response:**
```
200 OK
```

---

## 🌐 **Deploy em Produção**

### **1. Preparar VPS**

**Recomendação:** Hetzner CX33 (4 vCPUs, 8GB RAM, €13.43/mês)

```bash
# Conecte via SSH
ssh root@SEU_IP

# Instale Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sh get-docker.sh

# Instale Docker Compose
apt install docker-compose-plugin -y

# Configure Firewall
ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw enable
```

### **2. Clonar Projeto**

```bash
git clone https://github.com/luancal/calflow.git
cd calflow
```

### **3. Configurar .env.production**

```bash
nano .env.production
# Cole as variáveis de produção (veja seção Configuração)
```

### **4. Configurar Domínio (Cloudflare)**

1. Compre domínio (ex: `seubot.com.br`)
2. Adicione no Cloudflare
3. Crie registro DNS:
    - Tipo: A
    - Nome: api
    - Conteúdo: SEU_IP_VPS
    - Proxy: Ativado (nuvem laranja)

### **5. Configurar Caddy (SSL Automático)**

Crie `caddy/Caddyfile`:
```caddyfile
api.seubot.com.br {
    reverse_proxy evolution:8080
    
    header {
        Strict-Transport-Security "max-age=31536000"
        X-Content-Type-Options "nosniff"
        X-Frame-Options "DENY"
    }
}
```

### **6. Deploy**

```bash
# Build e Start
docker compose -f docker-compose.prod.yml up -d --build

# Verifique logs
docker compose -f docker-compose.prod.yml logs -f

# Verifique saúde
curl https://api.seubot.com.br
```

---

## 🗺️ **Roadmap**

### **v1.0 (Atual)**
- ✅ Chatbot conversacional com FSM
- ✅ Integração Google Calendar
- ✅ Lembretes automáticos
- ✅ Multi-tenancy básico

### **v1.1 (Q2 2026)**
- [ ] Dashboard administrativo (React)
- [ ] Analytics de conversas
- [ ] Exportação de relatórios (PDF/Excel)
- [ ] Webhooks customizáveis por cliente

### **v2.0 (Q3 2026)**
- [ ] IA Generativa (GPT-4 para respostas naturais)
- [ ] Integração com CRMs (RD Station, Pipedrive)
- [ ] Pagamento online via WhatsApp (Pix/Stripe)
- [ ] App mobile para gestão

---

## 📄 **Licença**

**© 2026 calflow. Todos os direitos reservados.**

Este software é proprietário. Não é permitido copiar, modificar, distribuir ou usar este código sem autorização expressa por escrito.

Para licenciamento comercial, entre em contato: calluann11@gmail.com

---

## 🤝 **Contato**

- **Email:** calluann11@gmail.com
- **WhatsApp:** +55 32 9940-1356

---

**Desenvolvido com ❤️ para automatizar agendamentos e liberar tempo para o que realmente importa.**