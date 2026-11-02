package br.com.barbershopdogui.model;

import java.util.List;
import java.util.Set;

/** Regras de seleção de vários serviços na mesma visita (sem framework). */
public final class ServiceSelectionRules {
    public static final int MAX_SERVICES = 3;
    public static final int MAX_MINUTES = 200;

    private ServiceSelectionRules() { }

    /** Serviço escolhido, com os ids que NÃO podem ser combinados com ele (tabela servico_exclusoes). */
    public record Item(long id, int minutes, Set<Long> excludes) { }

    /** @return null se a seleção é válida; senão a mensagem para o cliente. */
    public static String validate(List<Item> items) {
        if (items == null || items.isEmpty()) {
            return "Escolha ao menos um serviço.";
        }
        if (items.size() > MAX_SERVICES) {
            return "Escolha no máximo " + MAX_SERVICES + " serviços por visita.";
        }
        if (items.stream().map(Item::id).distinct().count() != items.size()) {
            return "Serviço repetido na seleção.";
        }
        int total = items.stream().mapToInt(Item::minutes).sum();
        if (total > MAX_MINUTES) {
            return "A visita pode ter no máximo " + MAX_MINUTES + " minutos.";
        }
        for (Item a : items) {
            for (Item b : items) {
                if (a != b && a.excludes().contains(b.id())) {
                    return "Esses serviços não podem ser combinados na mesma visita.";
                }
            }
        }
        return null;
    }
}
