package pe.agroruta.domain.model;

import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Reglas de la verificación en dos pasos del Dueño: al iniciar sesión le llega a su correo un código de 6 dígitos.
 * Cómo: el código vence en 10 minutos y admite 5 intentos fallidos; se puede pedir otro tras 60 segundos, hasta 3
 * veces por inicio de sesión y como máximo 10 códigos por hora.
 */
public final class ReglasVerificacion {

    public static final Duration VIGENCIA = Duration.ofMinutes(10);
    public static final int MAXIMO_INTENTOS = 5;
    public static final Duration ESPERA_REENVIO = Duration.ofSeconds(60);
    public static final int MAXIMO_REENVIOS = 3;
    public static final int MAXIMO_CODIGOS_POR_HORA = 10;

    public static final String CODIGO_VENCIDO = "El código venció o ya no sirve. Vuelve a iniciar sesión.";
    public static final String DEMASIADOS_CODIGOS =
            "Pediste muchos códigos en poco tiempo. Espera una hora y vuelve a intentarlo.";

    private static final Pattern FORMATO_CODIGO = Pattern.compile("^\\d{6}$");

    private ReglasVerificacion() {
    }

    /** Solo el Dueño de un negocio pasa el segundo paso; el Operador y el administrador entran con su clave. */
    public static boolean aplicaA(Rol rol, String identificadorNegocio) {
        return rol == Rol.DUENO && identificadorNegocio != null;
    }

    /** Quita espacios y guiones («123 456» o «123-456»); si no quedan 6 dígitos, avisa sin gastar un intento. */
    public static String normalizarCodigo(String codigo) {
        String limpio = codigo == null ? "" : codigo.replaceAll("[\\s-]", "");
        if (!FORMATO_CODIGO.matcher(limpio).matches()) {
            throw new DominioException("Escribe los 6 dígitos del código que te llegó al correo.");
        }
        return limpio;
    }

    /** Lo que se guarda: el código unido al desafío, para que el hash no sirva sin el desafío del navegador. */
    public static String textoAHashear(String desafio, String codigo) {
        return desafio + ":" + codigo;
    }

    /** «m*****2@gmail.com»: suficiente para reconocer el correo sin mostrarlo entero. */
    public static String ocultarCorreo(String correo) {
        int arroba = correo.indexOf('@');
        if (arroba <= 1) {
            return "*" + correo.substring(Math.max(arroba, 0));
        }
        String usuario = correo.substring(0, arroba);
        return usuario.charAt(0) + "*".repeat(Math.max(usuario.length() - 2, 1))
                + usuario.charAt(usuario.length() - 1) + correo.substring(arroba);
    }

    public static String asunto(String codigo) {
        return codigo + " es tu código para entrar a AgroRuta";
    }

    public static String cuerpo(String nombreUsuario, String identificadorNegocio, String codigo) {
        return "Hola, " + nombreUsuario + ".\n\n"
                + "Tu código para entrar a AgroRuta (negocio " + identificadorNegocio + ") es:\n\n"
                + "    " + codigo + "\n\n"
                + "Vence en " + VIGENCIA.toMinutes() + " minutos. No se lo compartas a nadie: AgroRuta nunca te lo "
                + "va a pedir por teléfono ni por WhatsApp.\n"
                + "Si no fuiste tú, alguien conoce tu contraseña: cámbiala cuanto antes.";
    }
}
