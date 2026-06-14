package io.newsworld.collector.util;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

/**
 * SSL utilities for scraping public news sites with broken/self-signed certificates.
 * NEVER use in contexts involving sensitive data or authentication.
 */
public final class SslUtils {

    private static final SSLSocketFactory TRUST_ALL_FACTORY;
    private static final HostnameVerifier TRUST_ALL_HOSTNAMES = (host, session) -> true;

    static {
        try {
            TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] c, String a) {}
                    public void checkServerTrusted(X509Certificate[] c, String a) {}
                }
            };
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, trustAll, new SecureRandom());
            SSLSocketFactory base = ctx.getSocketFactory();

            // Wrap to also disable TLS endpoint identification (Java 17+ hostname check
            // inside the TLS layer, separate from HttpsURLConnection.setHostnameVerifier).
            TRUST_ALL_FACTORY = new SSLSocketFactory() {
                private SSLSocket configure(SSLSocket s) {
                    SSLParameters p = s.getSSLParameters();
                    // "" disables endpoint identification; null would be overridden
                    // by HttpsClient which only skips the override when non-null.
                    p.setEndpointIdentificationAlgorithm("");
                    s.setSSLParameters(p);
                    return s;
                }
                public String[] getDefaultCipherSuites() { return base.getDefaultCipherSuites(); }
                public String[] getSupportedCipherSuites() { return base.getSupportedCipherSuites(); }
                public Socket createSocket(Socket s, String host, int port, boolean ac) throws IOException {
                    return configure((SSLSocket) base.createSocket(s, host, port, ac));
                }
                public Socket createSocket(String host, int port) throws IOException {
                    return configure((SSLSocket) base.createSocket(host, port));
                }
                public Socket createSocket(String host, int port, InetAddress la, int lp) throws IOException {
                    return configure((SSLSocket) base.createSocket(host, port, la, lp));
                }
                public Socket createSocket(InetAddress host, int port) throws IOException {
                    return configure((SSLSocket) base.createSocket(host, port));
                }
                public Socket createSocket(InetAddress host, int port, InetAddress la, int lp) throws IOException {
                    return configure((SSLSocket) base.createSocket(host, port, la, lp));
                }
            };
        } catch (Exception e) {
            throw new RuntimeException("Failed to init trust-all SSL context", e);
        }
    }

    public static SSLSocketFactory trustAllFactory() { return TRUST_ALL_FACTORY; }
    public static HostnameVerifier trustAllHostnames() { return TRUST_ALL_HOSTNAMES; }

    /** Applies trust-all to an HttpsURLConnection before connecting. */
    public static void disableSsl(java.net.HttpURLConnection conn) {
        if (conn instanceof HttpsURLConnection https) {
            https.setSSLSocketFactory(TRUST_ALL_FACTORY);
            https.setHostnameVerifier(TRUST_ALL_HOSTNAMES);
        }
    }

    private SslUtils() {}
}
