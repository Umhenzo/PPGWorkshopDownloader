package top.apricityx.workshop.workshop

import top.apricityx.workshop.steam.protocol.SteamAuthenticationException
import top.apricityx.workshop.steam.protocol.SteamProtocolException
import top.apricityx.workshop.steam.protocol.SteamServiceMethodException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

internal fun Throwable.userVisibleDownloadFailureMessage(): String {
    val causes = generateSequence(this) { it.cause }.toList()

    // 1. Check for specific Steam service method errors (e.g. EResult)
    causes.filterIsInstance<SteamServiceMethodException>().firstOrNull()?.let { serviceError ->
        return when (serviceError.resultCode) {
            15 -> "Acesso negado pela Steam (EResult=15). O item da Workshop pode ser privado ou exigir autorização de conta."
            3 -> "Sem conexão com os servidores da Steam (EResult=3)."
            8 -> "Parâmetros de requisição inválidos ou rejeitados pela Steam (EResult=8)."
            20 -> "Serviço da Steam temporariamente indisponível (EResult=20)."
            84, 87 -> "Muitas requisições enviadas à Steam. Tente novamente mais tarde (EResult=${serviceError.resultCode})."
            else -> "Falha na requisição de serviço da Steam '${serviceError.methodName}' (EResult=${serviceError.resultCode}${serviceError.steamMessage?.let { ": $it" } ?: ""})."
        }
    }

    // 2. Check for Steam authentication exceptions
    causes.filterIsInstance<SteamAuthenticationException>().firstOrNull()?.let { authError ->
        return authError.message ?: "Falha de autenticação na Steam (EResult=${authError.resultCode})."
    }

    // 3. Check for specific WorkshopDownloadException messages
    causes.filterIsInstance<WorkshopDownloadException>().firstOrNull()?.let { downloadError ->
        val msg = downloadError.message.orEmpty().trim()
        if (msg.isNotBlank() &&
            !msg.startsWith("Steam CDN request exhausted retries") &&
            !msg.equals("Steam CDN request failed: 401", ignoreCase = true) &&
            !msg.equals("Unable to download UGC manifest", ignoreCase = true)
        ) {
            return msg
        }
    }

    // 4. Check for network / socket exceptions
    causes.firstOrNull { it is SocketTimeoutException || it is UnknownHostException || it is IOException }?.let { netError ->
        val msg = netError.message?.takeIf { it.isNotBlank() }
        return when (netError) {
            is SocketTimeoutException -> "Tempo limite de conexão excedido com os servidores da Steam."
            is UnknownHostException -> "Não foi possível resolver o endereço dos servidores da Steam. Verifique sua conexão com a internet."
            else -> "Falha de rede/conexão com a Steam: ${msg ?: "Erro de rede"}"
        }
    }

    // 5. Check for general SteamProtocolException
    causes.filterIsInstance<SteamProtocolException>().firstOrNull()?.let { protocolError ->
        return protocolError.message ?: "Falha no protocolo de comunicação com a Steam."
    }

    // Fallback to first non-blank, non-generic message
    val messages = causes.mapNotNull { it.message?.trim()?.takeIf(String::isNotBlank) }
    return messages.firstOrNull { msg ->
        !msg.equals("Steam CDN request failed: 401", ignoreCase = true) &&
        !msg.startsWith("Steam CDN request exhausted retries") &&
        !msg.equals("Unable to download UGC manifest", ignoreCase = true)
    }
        ?: messages.firstOrNull()
        ?: (this::class.simpleName ?: "Falha no download")
}
