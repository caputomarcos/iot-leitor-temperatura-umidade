# ADR: Integração do Sensor de Temperatura e Umidade com MQTT

## Status
Aceito

## Data
2025-01-25

## Decisores
Equipe de Desenvolvimento, Arquiteto de Software

## Consultados
Especialistas em IoT, Produto, DevOps

## Informados
Gestores de Projeto, Operações

---

## Contexto e Declaração do Problema

O projeto tem como objetivo criar um sistema IoT que colete dados de sensores de temperatura e umidade conectados via porta serial e publique esses dados em um broker MQTT para consumo por outras aplicações.

Desafios técnicos incluem:
1. Gerenciar comunicação assíncrona e garantir a integridade dos dados.
2. Implementar políticas de reconexão e retry para garantir robustez.
3. Fornecer um sistema que seja modular e fácil de manter.

Além disso, o projeto precisa suportar a integração com brokers MQTT padrão (como o Eclipse Mosquitto) e operar de forma confiável em ambientes de produção.

---

## Motivadores da Decisão

- **Escalabilidade:** Permitir que múltiplos consumidores acessem os dados publicados no broker MQTT.
- **Confiabilidade:** Garantir que os dados sejam publicados mesmo em cenários de falhas temporárias na comunicação MQTT.
- **Manutenção:** Tornar o sistema modular, permitindo melhorias e integrações futuras.

---

## Opções Consideradas

### Opção 1: Comunicação Direta com o MQTT (Atual)
- O sistema se conecta diretamente ao broker MQTT usando a biblioteca Eclipse Paho.
- Publicação dos dados ocorre após a leitura e processamento do sensor.
- Política de retry gerenciada pelo Resilience4j.

**Prós:**
- Solução simples e direta.
- Suporte robusto a reconexões e retries via Resilience4j.
- Integração direta com bibliotecas de MQTT amplamente usadas.

**Contras:**
- Maior complexidade na configuração e gerenciamento do cliente MQTT.
- Necessidade de adaptar o código para cada mudança no protocolo MQTT.

---

### Opção 2: Uso de um Middleware (por exemplo, Apache Kafka)
- Dados do sensor são enviados para um middleware como o Kafka, que gerencia a distribuição para consumidores interessados.

**Prós:**
- Suporte nativo a vários consumidores e reprocessamento de mensagens.
- Facilidade de escalar a aplicação sem mudanças significativas.

**Contras:**
- Introdução de mais uma camada na arquitetura, aumentando a complexidade.
- Requer mudanças substanciais no código existente.
- Sobrecarga de infraestrutura para projetos menores.

---

### Opção 3: Implementar um Cache Local Temporário
- Os dados do sensor são armazenados localmente antes de serem publicados no MQTT.

**Prós:**
- Permite reprocessamento local em caso de falha no envio MQTT.
- Menor dependência de comunicação imediata com o broker.

**Contras:**
- Maior consumo de recursos no dispositivo local.
- Risco de perda de dados se o cache não for gerenciado corretamente.

---

## Decisão Tomada

Optamos por **manter a comunicação direta com o broker MQTT** utilizando a biblioteca Eclipse Paho e o Resilience4j para políticas de retry. Essa abordagem oferece simplicidade, confiabilidade e facilidade de integração com os requisitos atuais do projeto.

---

## Consequências

**Boas Consequências:**
- Comunicação eficiente com o broker MQTT.
- Menor sobrecarga de infraestrutura.
- Solução amplamente documentada e suportada pela comunidade.

**Más Consequências:**
- Requer configuração cuidadosa para evitar desconexões e falhas no envio.
- Aumenta a responsabilidade do código em gerenciar o estado da conexão MQTT.

---

## Validação

A solução será validada através de:
1. Testes de integração com um broker MQTT simulado (Eclipse Mosquitto).
2. Testes de falha simulando desconexões e interrupções de rede.
3. Logs detalhados para rastrear eventos de reconexão e mensagens publicadas.

---

## Mais Informações

- Documentação da biblioteca [Eclipse Paho](https://projects.eclipse.org/projects/iot.paho).
- Configuração do broker MQTT no Docker Compose.
- Exemplos de testes de integração no repositório GitHub do projeto.



---

When loaded, create 8 ttys interconnected:

/dev/tnt0 <=> /dev/tnt1
/dev/tnt2 <=> /dev/tnt3
/dev/tnt4 <=> /dev/tnt5
/dev/tnt6 <=> /dev/tnt7

the connection is:

TX -> RX
RX <- TX
RTS -> CTS
CTS <- RTS
DSR <- DTR
CD <- DTR
DTR -> DSR
DTR -> CD


```
○ → socat -d -d pty,raw,echo=0 pty,raw,echo=0
2025/01/20 23:05:50 socat[3267235] N PTY is /dev/pts/3
2025/01/20 23:05:50 socat[3267235] N PTY is /dev/pts/4
2025/01/20 23:05:50 socat[3267235] N starting data transfer loop with FDs [5,5] and [7,7]
```


```
± |main ✓| → sudo socat -d -d /dev/tnt0 /dev/pts/3
2025/01/20 23:07:27 socat[3269668] N opening character device "/dev/tnt0" for reading and writing
2025/01/20 23:07:27 socat[3269668] N opening character device "/dev/pts/3" for reading and writing
2025/01/20 23:07:27 socat[3269668] N starting data transfer loop with FDs [5,5] and [6,6]
```



```
sudo echo "TEMP:25.5" > /dev/tnt0
```

![image](https://github.com/user-attachments/assets/6c5fee2a-e0a7-41cd-a71a-e820611046b6)

![image](https://github.com/user-attachments/assets/a280e3df-4606-4f97-9c90-5024c2c85d3d)

![image](https://github.com/user-attachments/assets/85d13fb6-f3e2-48e8-8f2d-9892a5b761fc)


https://reports.cucumber.io/reports/76b4498a-43f6-4563-aa6c-a8e934fb3d3a










