package com.mrzym.blog.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HS256 JWT 工具（手写实现，不依赖 jjwt）。
 *
 * 为什么不用 jjwt：jjwt 0.12 在**签发和验签两侧**都强制要求 HMAC 密钥 >= 256 位，
 * 而原 Node 实现用的是很短的 `JWT_SECRET=blog`（4 字节），直接抛
 * "The verification key's size is 32 bits which is not secure enough for the HS256 algorithm"。
 * 迁移期要和现网 Node 互相识别 token，就必须按原始字节做 HMAC，
 * 所以这里用 javax.crypto.Mac 自己算，行为与 jsonwebtoken 一致。
 *
 * 安全提醒：密钥过短只做告警。生产环境请把 JWT_SECRET 换成 32 字节以上随机串，
 * 并且 Node 与 Java 两侧同步修改（否则两边签发的 token 互不认）。
 */
@Slf4j
public class JwtUtil {

    private static final String HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final long EXPIRE_SECONDS = 24L * 60 * 60;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final byte[] secret;

    public JwtUtil(String secret) {
        if (secret == null || secret.isEmpty()) {
            throw new IllegalStateException("JWT_SECRET 不能为空");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        if (this.secret.length < 32) {
            log.warn("JWT_SECRET 长度只有 {} 字节，低于 HS256 建议的 32 字节。"
                            + "迁移期为了兼容 Node 保持现状，上线前请换成强随机串（两侧同步修改）。",
                    this.secret.length);
        }
    }

    /** 签发：payload 里的 iat/exp 未提供时自动补上（等价于原实现 expiresIn: "1d"） */
    public String sign(Map<String, Object> payload) {
        Map<String, Object> claims = new LinkedHashMap<>(payload);
        long now = System.currentTimeMillis() / 1000;
        claims.putIfAbsent("iat", now);
        claims.putIfAbsent("exp", now + EXPIRE_SECONDS);

        String header = encode(HEADER_JSON.getBytes(StandardCharsets.UTF_8));
        String body;
        try {
            body = encode(MAPPER.writeValueAsBytes(claims));
        } catch (Exception e) {
            throw new IllegalStateException("JWT payload 序列化失败", e);
        }
        String signingInput = header + "." + body;
        return signingInput + "." + encode(hmac(signingInput));
    }

    /** 验签 + 校验有效期，返回 payload；失败抛 TokenException */
    public Map<String, Object> parse(String token) {
        if (token == null || token.isEmpty()) {
            throw new TokenException("token 为空", false);
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new TokenException("token 格式错误", false);
        }

        String signingInput = parts[0] + "." + parts[1];
        byte[] actual;
        try {
            actual = DECODER.decode(parts[2]);
        } catch (IllegalArgumentException e) {
            throw new TokenException("token 签名编码错误", false);
        }
        // 常量时间比较，避免时序侧信道
        if (!MessageDigest.isEqual(hmac(signingInput), actual)) {
            throw new TokenException("token 签名不匹配", false);
        }

        Map<String, Object> claims;
        try {
            byte[] json = DECODER.decode(parts[1]);
            claims = MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw new TokenException("token 载荷解析失败", false);
        }

        Object exp = claims.get("exp");
        if (exp instanceof Number number) {
            long nowSeconds = System.currentTimeMillis() / 1000;
            if (nowSeconds > number.longValue()) {
                throw new TokenException("token 已过期", true);
            }
        }
        return claims;
    }

    private byte[] hmac(String input) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 计算失败", e);
        }
    }

    private static String encode(byte[] bytes) {
        return ENCODER.encodeToString(bytes);
    }
}
