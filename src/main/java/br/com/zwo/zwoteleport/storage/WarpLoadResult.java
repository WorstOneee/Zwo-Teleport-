package br.com.zwo.zwoteleport.storage;

import br.com.zwo.zwoteleport.warp.WarpPoint;

import java.util.List;
import java.util.Map;

/**
 * Resultado de uma carga de warps.
 *
 * @param warps warps válidas, por nome normalizado
 * @param warnings problemas encontrados que não impediram a carga
 * @param source de onde os dados vieram
 */
public record WarpLoadResult(Map<String, WarpPoint> warps, List<String> warnings, Source source) {

    public enum Source {
        /** Arquivo principal lido normalmente. */
        PRIMARY,
        /** Arquivo ainda não existia; começa vazio. */
        NEW,
        /** Arquivo principal ilegível; dados vieram do backup. */
        BACKUP,
        /** Nada pôde ser lido; armazenamento fica somente leitura para não sobrescrever o arquivo. */
        UNREADABLE
    }

    public WarpLoadResult {
        warps = Map.copyOf(warps);
        warnings = List.copyOf(warnings);
    }
}
