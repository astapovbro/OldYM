package ru.oldym;

import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.*;
import java.util.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.tls.*;
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto;

/** Small HTTPS transport independent of the Android platform TLS stack. */
public final class Net {
    private static final Provider PROVIDER = new BouncyCastleProvider();
    private static Set<TrustAnchor> roots;
    private Net() {}

    public static synchronized void init(InputStream pem) throws Exception {
        if (roots != null) { pem.close(); return; }
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509", PROVIDER);
            Set<TrustAnchor> loaded = new HashSet<TrustAnchor>();
            for (java.security.cert.Certificate c : factory.generateCertificates(pem)) {
                loaded.add(new TrustAnchor((X509Certificate)c, null));
            }
            if (loaded.isEmpty()) throw new IOException("Нет корневых сертификатов");
            roots = loaded;
        } finally { pem.close(); }
    }

    private static boolean dnsMatch(String host, String pattern) {
        host = host.toLowerCase(Locale.US);
        pattern = pattern.toLowerCase(Locale.US);
        if (host.equals(pattern)) return true;
        if (!pattern.startsWith("*.")) return false;
        String suffix = pattern.substring(1);
        return host.endsWith(suffix) && host.length() > suffix.length()
            && host.substring(0, host.length()-suffix.length()).indexOf('.') == -1;
    }

    private static void verifyHost(String host, X509Certificate cert) throws Exception {
        Collection<List<?>> names = cert.getSubjectAlternativeNames();
        if (names != null) {
            for (List<?> item : names) {
                if (Integer.valueOf(2).equals(item.get(0)) && dnsMatch(host, (String)item.get(1))) return;
            }
        }
        throw new IOException("Сертификат не соответствует серверу " + host);
    }

    private static void verify(String host, org.bouncycastle.tls.Certificate chain) throws IOException {
        try {
            if (roots == null || chain.isEmpty()) throw new IOException("Нет доверенных сертификатов");
            CertificateFactory cf = CertificateFactory.getInstance("X.509", PROVIDER);
            List<X509Certificate> certs = new ArrayList<X509Certificate>();
            for (int i=0; i<chain.getLength(); i++) {
                X509Certificate cert = (X509Certificate) cf.generateCertificate(
                    new ByteArrayInputStream(chain.getCertificateAt(i).getEncoded()));
                cert.checkValidity();
                certs.add(cert);
            }
            verifyHost(host, certs.get(0));
            List<String> usage = certs.get(0).getExtendedKeyUsage();
            if (usage != null && !usage.contains("1.3.6.1.5.5.7.3.1") && !usage.contains("2.5.29.37.0"))
                throw new IOException("Сертификат не предназначен для HTTPS");
            boolean[] keyUsage = certs.get(0).getKeyUsage();
            if (keyUsage != null && !keyUsage[0]) throw new IOException("Неверное назначение ключа");
            X509CertSelector selector = new X509CertSelector();
            selector.setCertificate(certs.get(0));
            PKIXBuilderParameters params = new PKIXBuilderParameters(roots, selector);
            params.setRevocationEnabled(false);
            params.addCertStore(CertStore.getInstance("Collection", new CollectionCertStoreParameters(certs), PROVIDER));
            CertPathBuilder.getInstance("PKIX", PROVIDER).build(params);
        } catch (Exception e) { throw new IOException("Проверка HTTPS: " + e.toString()); }
    }

    public static final class Response {
        public final int code;
        public final Map<String,String> headers;
        public final InputStream body;
        private final Socket socket;
        private Response(int c, Map<String,String> h, InputStream b, Socket s) {
            code=c; headers=h; body=b; socket=s;
        }
        public void close() { try { body.close(); } catch(Exception ignored) {} try { socket.close(); } catch(Exception ignored) {} }
    }

    static String line(InputStream input) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int v;
        while ((v=input.read()) != -1) {
            if (v == '\n') break;
            if (v != '\r') b.write(v);
            if (b.size()>16384) throw new IOException("Слишком длинный заголовок");
        }
        if (v == -1 && b.size()==0) return null;
        return new String(b.toByteArray(), "ISO-8859-1");
    }

    static final class Chunked extends InputStream {
        private final InputStream in;
        private long remaining;
        private boolean first=true, ended;
        Chunked(InputStream i) { in=i; }
        private boolean ready() throws IOException {
            if (ended) return false;
            if (remaining==0) {
                if (!first && !"".equals(line(in))) throw new IOException("Ошибка HTTP chunk");
                first=false;
                String size=line(in);
                if(size==null) throw new EOFException();
                int semi=size.indexOf(';');
                if(semi>=0)size=size.substring(0,semi);
                try { remaining=Long.parseLong(size.trim(),16); } catch(Exception e) { throw new IOException("Ошибка размера HTTP chunk"); }
                if(remaining<0)throw new IOException("Неверный размер HTTP chunk");
                if(remaining==0) { ended=true; return false; }
            }
            return true;
        }
        public int read() throws IOException { byte[] b=new byte[1]; return read(b,0,1)==-1?-1:b[0]&255; }
        public int read(byte[] b,int o,int l) throws IOException {
            if(l==0)return 0;
            if(!ready())return -1;
            int n=in.read(b,o,(int)Math.min(l,remaining));
            if(n<0)throw new EOFException("Оборванный HTTP chunk");
            remaining-=n; return n;
        }
        public void close() throws IOException { in.close(); }
    }

    static final class FixedLength extends InputStream {
        private final InputStream input;
        private long remaining;
        FixedLength(InputStream in,long size){input=in;remaining=size;}
        public int read()throws IOException{
            byte[] b=new byte[1];return read(b,0,1)==-1?-1:b[0]&255;
        }
        public int read(byte[] b,int offset,int length)throws IOException{
            if(length==0)return 0;if(remaining==0)return -1;
            int n=input.read(b,offset,(int)Math.min(length,remaining));
            if(n<0)throw new EOFException("Оборванный ответ сервера");
            remaining-=n;return n;
        }
        public void close()throws IOException{input.close();}
    }

    public static Response open(String address, String token, String form) throws Exception {
        return open(address,token,form,0);
    }
    private static Response open(String address, String token, String form, int redirects) throws Exception {
        final URL url = new URL(address);
        if(!"https".equals(url.getProtocol()) || url.getUserInfo()!=null)throw new IOException("Разрешён только HTTPS");
        final String host=url.getHost();
        if(host.length()==0 || host.indexOf('\r')>=0 || host.indexOf('\n')>=0)throw new IOException("Неверный сервер");
        final Socket socket=new Socket();
        try {
            socket.connect(new InetSocketAddress(host,url.getPort()<0?443:url.getPort()),20000);
            socket.setSoTimeout(30000);
            TlsClientProtocol tls=new TlsClientProtocol(socket.getInputStream(),socket.getOutputStream());
            tls.connect(new DefaultTlsClient(new BcTlsCrypto(new SecureRandom())) {
                protected ProtocolVersion[] getSupportedVersions() { return new ProtocolVersion[] { ProtocolVersion.TLSv12 }; }
                protected Vector getSNIServerNames() {
                    Vector v=new Vector();
                    try { v.add(new ServerName(NameType.host_name,host.getBytes("US-ASCII"))); } catch(Exception e) { throw new IllegalStateException(); }
                    return v;
                }
                public TlsAuthentication getAuthentication() {
                    return new TlsAuthentication() {
                        public void notifyServerCertificate(TlsServerCertificate c) throws IOException { verify(host,c.getCertificate()); }
                        public TlsCredentials getClientCredentials(CertificateRequest r) { return null; }
                    };
                }
            });
            String path=url.getFile(); if(path.length()==0)path="/";
            if(path.indexOf('\r')>=0 || path.indexOf('\n')>=0)throw new IOException("Неверный адрес");
            byte[] payload=form==null?new byte[0]:form.getBytes("UTF-8");
            StringBuilder request=new StringBuilder(form==null?"GET ":"POST ").append(path).append(" HTTP/1.1\r\nHost: ").append(host)
                .append("\r\nUser-Agent: RetroMusic/0.3\r\nAccept: */*\r\nAccept-Encoding: identity\r\nConnection: close\r\n");
            if(token!=null && token.length()>0 && "api.music.yandex.net".equals(host)) {
                if(!token.matches("[A-Za-z0-9_\\-.]+"))throw new IOException("В токене лишние символы");
                request.append("Authorization: OAuth ").append(token).append("\r\n");
            }
            if(form!=null)request.append("Content-Type: application/x-www-form-urlencoded\r\nContent-Length: ").append(payload.length).append("\r\n");
            request.append("\r\n");
            OutputStream out=tls.getOutputStream();out.write(request.toString().getBytes("UTF-8"));out.write(payload);out.flush();
            InputStream in=new BufferedInputStream(tls.getInputStream(),8192);
            String status=line(in);
            if(status==null || !status.startsWith("HTTP/"))throw new IOException("Нет ответа HTTP");
            int code=Integer.parseInt(status.split(" ")[1]);
            Map<String,String> headers=new HashMap<String,String>();
            for(int count=0;count<100;count++) {
                String h=line(in); if(h==null)throw new EOFException(); if(h.length()==0)break;
                int colon=h.indexOf(':');if(colon>0)headers.put(h.substring(0,colon).toLowerCase(Locale.US),h.substring(colon+1).trim());
                if(count==99)throw new IOException("Слишком много заголовков");
            }
            if(code==301||code==302||code==303||code==307||code==308) {
                if(redirects>=5 || !headers.containsKey("location"))throw new IOException("Ошибка перенаправления");
                String next=new URL(url,headers.get("location")).toString();socket.close();
                return open(next,token,code==307||code==308?form:null,redirects+1);
            }
            InputStream body;
            if("chunked".equalsIgnoreCase(headers.get("transfer-encoding")))body=new Chunked(in);
            else if(headers.containsKey("content-length")){
                long size=Long.parseLong(headers.get("content-length"));
                if(size<0)throw new IOException("Неверная длина ответа");
                body=new FixedLength(in,size);
            }else body=in;
            return new Response(code,headers,body,socket);
        } catch(Exception e) { try{socket.close();}catch(Exception ignored){} throw e; }
    }
    public static byte[] bytes(String url,String token,String form,int limit) throws Exception {
        Response r=open(url,token,form);
        try {
            if(r.code<200||r.code>=300)throw new IOException("HTTP "+r.code+(r.code==401?": проверь токен":r.code==403?": нет доступа к ресурсу":""));
            ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
            while((n=r.body.read(buf))!=-1) { if(b.size()+n>limit)throw new IOException("Слишком большой ответ");b.write(buf,0,n); }
            return b.toByteArray();
        } finally {r.close();}
    }
    public static String text(String url,String token,String form) throws Exception {
        return new String(bytes(url,token,form,8*1024*1024),"UTF-8");
    }
    public interface Progress { void update(long bytes); boolean cancelled(); }
    public static void download(String url, File target, Progress listener) throws Exception {
        Response r=open(url,null,null);
        try {
            if(r.code!=200)throw new IOException("Загрузка: HTTP "+r.code);
            String length=r.headers.get("content-length");
            long expected=length==null?-1:Long.parseLong(length);
            if(expected>100*1024*1024)throw new IOException("Файл больше 100 МБ");
            FileOutputStream out=new FileOutputStream(target);long total=0;
            try {
                byte[] buf=new byte[16384];int n;
                while((n=r.body.read(buf))!=-1) {
                    if(listener!=null && listener.cancelled())throw new IOException("Загрузка отменена");
                    total+=n;if(total>100*1024*1024)throw new IOException("Файл больше 100 МБ");
                    out.write(buf,0,n);if(listener!=null)listener.update(total);
                }
                if(expected>=0 && total!=expected)throw new IOException("Файл загрузился не полностью");
            } finally {out.close();}
        } finally {r.close();}
    }
}
