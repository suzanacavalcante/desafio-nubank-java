package io.github.suzanacavalcante.desafio.suporte;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Repassador TCP mínimo para simular a <b>queda e a volta do banco</b> sem parar o container: a aplicação conecta
 * no proxy; {@link #cortar()} derruba as conexões abertas e recusa novas (como um banco fora do ar) e
 * {@link #religar()} volta a aceitar na <b>mesma porta</b> (como um banco que voltou).
 */
public final class ProxyTcp implements AutoCloseable {

    private final String destinoHost;
    private final int destinoPorta;
    private final int porta;
    private final Set<Socket> abertos = ConcurrentHashMap.newKeySet();
    private volatile ServerSocket servidor;
    private volatile boolean congelado;

    public ProxyTcp(String destinoHost, int destinoPorta) throws IOException {
        this.destinoHost = destinoHost;
        this.destinoPorta = destinoPorta;
        this.servidor = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        this.porta = servidor.getLocalPort();
        aceitar(servidor);
    }

    public int porta() {
        return porta;
    }

    /** Banco "fora do ar": fecha o listener e todas as conexões abertas. */
    public synchronized void cortar() throws IOException {
        servidor.close();
        for (Socket s : abertos) {
            fechar(s);
        }
        abertos.clear();
    }

    /**
     * Banco que <b>trava sem recusar</b>: as conexões continuam abertas, mas nada mais é repassado (como um host
     * que cai sem RST, ou um firewall que descarta pacotes).
     */
    public void congelar() {
        congelado = true;
    }

    public void descongelar() {
        congelado = false;
    }

    /** Banco "de volta": volta a escutar na mesma porta. */
    public synchronized void religar() throws IOException {
        servidor = new ServerSocket(porta, 50, InetAddress.getLoopbackAddress());
        aceitar(servidor);
    }

    private void aceitar(ServerSocket escuta) {
        Thread.ofPlatform().daemon().name("proxy-tcp-aceita").start(() -> {
            while (!escuta.isClosed()) {
                try {
                    Socket cliente = escuta.accept();
                    Socket banco = new Socket(destinoHost, destinoPorta);
                    abertos.add(cliente);
                    abertos.add(banco);
                    repassar(cliente, banco);
                    repassar(banco, cliente);
                } catch (IOException e) {
                    // listener fechado (cortar) ou destino indisponível: encerra/ignora
                }
            }
        });
    }

    private void repassar(Socket de, Socket para) {
        Thread.ofPlatform().daemon().name("proxy-tcp-repassa").start(() -> {
            try (InputStream in = de.getInputStream(); OutputStream out = para.getOutputStream()) {
                byte[] buffer = new byte[8192];
                int lidos;
                while ((lidos = in.read(buffer)) != -1) {
                    while (congelado) {
                        Thread.sleep(20);
                    }
                    out.write(buffer, 0, lidos);
                    out.flush();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                // conexão derrubada
            } finally {
                fechar(de);
                fechar(para);
                abertos.remove(de);
                abertos.remove(para);
            }
        });
    }

    private static void fechar(Socket s) {
        try {
            s.close();
        } catch (IOException ignorado) {
            // nada a fazer
        }
    }

    @Override
    public void close() throws IOException {
        cortar();
    }
}
