package com.brunosong.identityplatform.auth.service.application.identity.token;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * base64(DER) 로 주입된 RSA 키를 디코드하는 유틸. 토큰 서명/검증을 realm별 비대칭 키로 분리할 때
 * 발급기(private)와 검증기(public)가 공유한다. private=PKCS#8, public=X.509 SubjectPublicKeyInfo.
 */
public final class RsaKeys {

    private RsaKeys() {
    }

    /** base64 로 인코딩된 PKCS#8 DER private key. */
    public static PrivateKey privateKeyFromBase64Der(String base64Der) {
        try {
            byte[] der = Base64.getDecoder().decode(base64Der);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("유효하지 않은 RSA private key (base64 PKCS#8 DER)", e);
        }
    }

    /** base64 로 인코딩된 X.509 DER public key. */
    public static PublicKey publicKeyFromBase64Der(String base64Der) {
        try {
            byte[] der = Base64.getDecoder().decode(base64Der);
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("유효하지 않은 RSA public key (base64 X.509 DER)", e);
        }
    }
}
