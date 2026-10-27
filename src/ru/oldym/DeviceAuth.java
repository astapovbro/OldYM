package ru.oldym;

import java.io.*;
import java.util.UUID;
import org.json.JSONObject;

/** OAuth Device Flow. The user approves access only on Yandex's own website. */
public final class DeviceAuth {
    private static final String CLIENT="23cabbbdc6cd418abb4b39c32c41195d";
    private static final String SECRET="53bc75238f0c4d08a118e51fe9203300";
    private DeviceAuth(){}
    private static JSONObject post(String path,String form)throws Exception{
        Net.Response response=Net.open("https://oauth.yandex.ru"+path,null,form);
        try{
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;
            while((n=response.body.read(buffer))!=-1){
                if(bytes.size()+n>65536)throw new IOException("Слишком большой ответ авторизации");
                bytes.write(buffer,0,n);
            }
            JSONObject result=new JSONObject(new String(bytes.toByteArray(),"UTF-8"));
            if(response.code!=200&&!result.has("error"))throw new IOException("Авторизация: HTTP "+response.code);
            return result;
        }finally{response.close();}
    }
    public static JSONObject request()throws Exception{
        JSONObject code=post("/device/code","client_id="+CLIENT+"&device_id="+UUID.randomUUID().toString().replace("-","")+"&device_name="+Api.enc("Retro Music Android 2"));
        if(code.has("error"))throw new IOException("Не удалось получить код: "+code.optString("error"));
        code.getString("device_code");code.getString("user_code");
        return code;
    }
    public static JSONObject poll(String code)throws Exception{
        return post("/token","grant_type=device_code&code="+Api.enc(code)+"&client_id="+CLIENT+"&client_secret="+SECRET);
    }
    public static String verificationUrl(JSONObject code)throws Exception{
        String url=code.optString("verification_url",code.optString("verification_uri",""));
        java.net.URL parsed=new java.net.URL(url);
        String host=parsed.getHost();
        if(!"https".equals(parsed.getProtocol())||parsed.getUserInfo()!=null
            ||!("ya.ru".equals(host)||"yandex.ru".equals(host)||host.endsWith(".yandex.ru")))
            throw new IOException("Неизвестная страница подтверждения Яндекса");
        return url;
    }
}
