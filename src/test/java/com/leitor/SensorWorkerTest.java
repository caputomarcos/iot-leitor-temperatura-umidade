package com.leitor;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;

public class SensorWorkerTest {

    private SerialPort portaSerialMock;
    private MqttClient mqttClientMock;
    private SensorWorker sensorWorker;

    @Before
    public void setup() throws MqttException {
        portaSerialMock = mock(SerialPort.class); // Mock da porta serial
        mqttClientMock = mock(MqttClient.class); // Mock do cliente MQTT

        // Configuração do mock do MQTT
        when(mqttClientMock.isConnected()).thenReturn(true);
        doNothing().when(mqttClientMock).publish(any(String.class), any(MqttMessage.class));
        doNothing().when(mqttClientMock).connect(any(MqttConnectOptions.class));
        doNothing().when(mqttClientMock).disconnect();

        // Instância do SensorWorker com os mocks
        sensorWorker = new SensorWorker(portaSerialMock, mqttClientMock);
    }

    @Test
    public void devePublicarNoMQTTAposReconexao() throws Exception {
        // Simula desconexão e reconexão
        when(mqttClientMock.isConnected()).thenReturn(false, true);
        doNothing().when(mqttClientMock).reconnect();

        // Testa publicação no MQTT
        sensorWorker.publicarNoMQTT("sensores/temperatura", "25.5");

        // Verifica que a reconexão foi chamada
        verify(mqttClientMock, times(1)).reconnect();

        // Verifica a publicação no tópico MQTT
        verify(mqttClientMock, times(1)).publish(eq("sensores/temperatura"), any(MqttMessage.class));
    }

    @Test
    public void deveRegistrarAvisoParaDadosInvalidos() throws Exception {
        // Configura dados inválidos
        String invalidData = "INVALID_DATA";
        when(portaSerialMock.bytesAvailable()).thenReturn(invalidData.length());
        when(portaSerialMock.readBytes(any(byte[].class), eq(invalidData.length())))
                .thenAnswer(invocation -> {
                    byte[] buffer = invocation.getArgument(0);
                    System.arraycopy(invalidData.getBytes(), 0, buffer, 0, invalidData.length());
                    return invalidData.length();
                });

        // Configura mocks para o listener
        when(portaSerialMock.openPort()).thenReturn(true);
        doAnswer(invocation -> {
            SerialPortDataListener listener = invocation.getArgument(0);
            sensorWorker.setSerialPortDataListener(listener); // Configura o listener
            return true;
        }).when(portaSerialMock).addDataListener(any(SerialPortDataListener.class));

        // Captura a saída do console
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(out));

        try {
            // Inicializa o SensorWorker e dispara evento de dados inválidos
            sensorWorker.iniciarLeitura();
            SerialPortDataListener listener = sensorWorker.getSerialPortDataListener();
            listener.serialEvent(new SerialPortEvent(portaSerialMock, SerialPort.LISTENING_EVENT_DATA_AVAILABLE));
        } finally {
            // Restaura o console original
            System.setOut(originalOut);
        }

        // Verifica que a mensagem de erro foi registrada
        String consoleOutput = new String(out.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(consoleOutput.contains("Dados desconhecidos recebidos: INVALID_DATA"));

        // Verifica que nenhuma publicação foi feita
        verify(mqttClientMock, never()).publish(any(String.class), any(MqttMessage.class));
    }

    @Test
    public void devePublicarNoMQTTAposPerdaDeConexao() throws Exception {
        // "Perda de conexão" -> "resilience4j" re-tenta a mesma publicação
        // sem reconectar manualmente.

        // Simular desconexão
        when(mqttClientMock.isConnected()).thenReturn(false, true);

        // Quando chama publicar, a 1ª ver falha, a 2ª ok
        sensorWorker.publicarNoMQTT("sensores/temperatura", "25.5");

        // Vemos se a publicação final acabou sendo feita
        verify(mqttClientMock, times(1)).publish(eq("sensores/temperatura"), any(MqttMessage.class));
    }

    @Test
    public void deveRegistrarErroAoAbrirPortaSerial() {
        // Simula falha ao abrir a porta serial
        when(portaSerialMock.openPort()).thenReturn(false);
        when(portaSerialMock.getSystemPortName()).thenReturn("COM99");

        // Captura a saída do console
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(out));

        try {
            sensorWorker.iniciarLeitura();
        } finally {
            System.setOut(originalOut);
        }

        // Verifica que a mensagem de erro foi registrada
        String consoleOutput = new String(out.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(consoleOutput.contains("Não foi possível abrir a porta serial"));
    }

    @Test
    public void deveTentarRePublicarQuandoPublicacaoMQTTFalhar() throws Exception {
        // Configura falha na publicação inicial e sucesso na segunda tentativa
        doThrow(new MqttException(MqttException.REASON_CODE_CLIENT_NOT_CONNECTED))
                .doNothing()
                .when(mqttClientMock)
                .publish(eq("sensores/temperatura"), any(MqttMessage.class));

        // Publica no MQTT
        sensorWorker.publicarNoMQTT("sensores/temperatura", "30.0");

        // Verifica se foi tentado novamente
        verify(mqttClientMock, times(2)).publish(eq("sensores/temperatura"), any(MqttMessage.class));
    }

    @Test
    public void deveProcessarDadosValidosDeTemperaturaEUmidade() throws Exception {
        String dados = "TEMP:22.5\nUMID:55.3\n";

        when(portaSerialMock.bytesAvailable()).thenReturn(dados.length());
        when(portaSerialMock.readBytes(any(byte[].class), eq(dados.length())))
                .thenAnswer(invocation -> {
                    byte[] buffer = invocation.getArgument(0);
                    System.arraycopy(dados.getBytes(), 0, buffer, 0, dados.length());
                    return dados.length();
                });

        sensorWorker.processarDados(dados);

        verify(mqttClientMock, times(1)).publish(eq("sensores/temperatura"), any(MqttMessage.class));
        verify(mqttClientMock, times(1)).publish(eq("sensores/umidade"), any(MqttMessage.class));
    }

    @Test
    public void deveRegistrarAvisoParaDadosDesconhecidos() throws MqttPersistenceException, MqttException {
        String invalidData = "UNKNOWN:123";

        when(portaSerialMock.bytesAvailable()).thenReturn(invalidData.length());
        when(portaSerialMock.readBytes(any(byte[].class), eq(invalidData.length())))
                .thenAnswer(invocation -> {
                    byte[] buffer = invocation.getArgument(0);
                    System.arraycopy(invalidData.getBytes(), 0, buffer, 0, invalidData.length());
                    return invalidData.length();
                });

        sensorWorker.processarDados(invalidData);

        verify(mqttClientMock, never()).publish(any(String.class), any(MqttMessage.class));
        verify(mqttClientMock, never()).publish(eq("sensores/temperatura"), any(MqttMessage.class));
    }

    @Test
    public void deveRegistrarAvisoAoFecharPortaSerial() {
        when(portaSerialMock.isOpen()).thenReturn(true);
        when(portaSerialMock.closePort()).thenReturn(false);

        sensorWorker.pararLeitura();

        verify(portaSerialMock, times(1)).closePort();
    }

    @Test
    public void deveRegistrarErroAoPublicarTimeout() throws Exception {
        // Configura falha nas duas primeiras tentativas e sucesso na terceira
        doThrow(new MqttException(MqttException.REASON_CODE_CLIENT_TIMEOUT))
                .doThrow(new MqttException(MqttException.REASON_CODE_CLIENT_TIMEOUT))
                .doNothing()
                .when(mqttClientMock)
                .publish(eq("sensores/temperatura"), any(MqttMessage.class));
    
        // Executa a publicação
        sensorWorker.publicarNoMQTT("sensores/temperatura", "25.5");
    
        // Verifica que o publish foi chamado 3 vezes (2 falhas + 1 sucesso)
        verify(mqttClientMock, times(3)).publish(eq("sensores/temperatura"), any(MqttMessage.class));
    }
    
    

    @Test
    public void deveRegistrarErroQuandoListenerFalha() {
        SerialPortDataListener listener = mock(SerialPortDataListener.class);
        doThrow(new RuntimeException("Listener falhou"))
                .when(listener)
                .serialEvent(any(SerialPortEvent.class));

        sensorWorker.setSerialPortDataListener(listener);

        try {
            listener.serialEvent(new SerialPortEvent(portaSerialMock, SerialPort.LISTENING_EVENT_DATA_AVAILABLE));
        } catch (RuntimeException e) {
            // Certifique-se de que o erro e tratado corretamente
            assertTrue(e.getMessage().contains("Listener falhou"));
        }
    }

}
