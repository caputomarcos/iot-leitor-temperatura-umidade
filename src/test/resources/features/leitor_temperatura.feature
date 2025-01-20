# language: pt
Funcionalidade: Leitura de temperatura e publicação via MQTT
  Como usuário do sistema
  Quero que o leitor de temperatura leia dados da porta serial
  E publique esses dados em um broker MQTT

  Cenário: Leitura e publicação bem-sucedidas
    Dado que a porta serial esta disponivel
    E o broker MQTT está acessível
    Quando o leitor de temperatura é executado
    Então os dados são lidos corretamente da porta serial
    E os dados de temperatura e umidade são publicados no broker MQTT

  Cenário: Falha ao abrir a porta serial
    Dado que a porta serial não está disponível
    Quando o leitor de temperatura é executado
    Então uma mensagem de erro é registrada
    E a leitura não é realizada

  Cenário: Falha ao conectar ao broker MQTT
    Dado que a porta serial esta disponivel
    E o broker MQTT não está acessível
    Quando o leitor de temperatura é executado
    Então uma mensagem de erro de conexão MQTT é registrada
    E nenhuma publicação é realizada

  Cenário: Dados inválidos recebidos da porta serial
    Dado que a porta serial esta disponivel
    E o broker MQTT está acessível
    Quando o leitor de temperatura recebe dados inválidos
    Então uma mensagem de alerta é registrada
    E nenhuma publicação é realizada

  Cenário: Interrupção durante a leitura
    Dado que a porta serial esta disponivel
    E o broker MQTT está acessível
    E o leitor de temperatura está em execução
    Quando o processo é interrompido
    Então o leitor para a leitura da porta serial
    E nenhuma publicação é realizada

  Cenário: Reconexão ao broker MQTT
    Dado que a porta serial esta disponivel
    E o broker MQTT está temporariamente indisponível
    Quando o broker MQTT volta a estar disponível
    Então os dados de temperatura e umidade são publicados novamente no broker MQTT
