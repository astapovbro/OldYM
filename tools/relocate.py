import zipfile,struct,pathlib
base=pathlib.Path(__file__).resolve().parent.parent

def u2(b,p):return struct.unpack_from('>H',b,p)[0]
def relocate(data):
    if data[:4]!=b'\xca\xfe\xba\xbe':return data
    count=u2(data,8);out=bytearray(data[:10]);p=10;i=1
    sizes={3:4,4:4,5:8,6:8,7:2,8:2,9:4,10:4,11:4,12:4,15:3,16:2,18:4,17:4,19:2,20:2}
    while i<count:
        tag=data[p];out.append(tag);p+=1
        if tag==1:
            n=u2(data,p);p+=2;text=data[p:p+n];p+=n
            text=text.replace(b'org/bouncycastle',b'ru/oldym/crypto').replace(b'org.bouncycastle',b'ru.oldym.crypto')
            out.extend(struct.pack('>H',len(text)));out.extend(text)
        else:
            n=sizes[tag];out.extend(data[p:p+n]);p+=n
            if tag in (5,6):i+=1
        i+=1
    out.extend(data[p:]);return out
for name in ['app','bctls','bcprov','bcutil']:
    src=base/'build/app.jar' if name=='app' else base/('tools/'+name+'.jar')
    dest=base/('build/'+name+'-relocated.jar')
    with zipfile.ZipFile(src)as z,zipfile.ZipFile(dest,'w',zipfile.ZIP_DEFLATED)as out:
        for f in z.infolist():
            if f.filename.startswith('META-INF/') or f.filename.endswith('module-info.class'):continue
            new=f.filename.replace('org/bouncycastle','ru/oldym/crypto')
            out.writestr(new,relocate(z.read(f.filename)))
