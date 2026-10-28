package br.com.zwo.zwoteleport.storage;

import br.com.zwo.zwoteleport.warp.WarpPoint;

/**
 * Persistência de warps.
 *
 * <p>As implementações podem bloquear (disco, rede) e por isso nunca são chamadas na thread
 * principal depois da inicialização: o {@code WarpService} usa um executor dedicado. Uma futura
 * implementação SQL deve guardar as warps por instância (coluna de servidor), porque cada servidor
 * tem seus próprios mundos; apenas warps explicitamente marcadas como globais fariam sentido
 * compartilhadas. Pedidos de teleporte e warmups nunca são persistidos.</p>
 */
public interface WarpRepository {

    /** Carrega todas as warps. Chamado na inicialização e no reload. */
    WarpLoadResult load();

    /** Cria ou atualiza uma warp. */
    void upsert(WarpPoint warp) throws StorageException;

    /** Remove uma warp. Devolve false se ela não existia no armazenamento. */
    boolean delete(String name) throws StorageException;

    /** Se o armazenamento aceita gravações neste momento. */
    boolean writable();

    /** Descrição curta para diagnóstico (sem senhas). */
    String describe();
}
