package com.rewabank.cards.scheduler;

import com.rewabank.cards.entity.Card;
import com.rewabank.cards.kafka.CardEventProducer;
import com.rewabank.cards.repository.CardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class CardExpiryScheduler {

    private final CardRepository    cardRepository;
    private final CardEventProducer eventProducer;

    // Run daily at 9 AM — check for expiring cards
    @Scheduled(cron = "0 0 9 * * ?")
    public void checkExpiringCards() {
        LocalDate today          = LocalDate.now();
        LocalDate thirtyDaysLater = today.plusDays(30);

        List<Card> expiringCards = cardRepository
                .findCardsExpiringIn30Days(today, thirtyDaysLater);

        log.info("Expiry check — found {} cards expiring in 30 days",
                expiringCards.size());

        expiringCards.forEach(card -> {
            try {
                // Publish expiry alert event — Notifications MS sends SMS
                eventProducer.publishCardBlocked(
                        card.getId().toString(),
                        card.getKeycloakUserId(),
                        card.getAccountId().toString(),
                        "Card expiring on " + card.getExpiryDate()
                );

                // Mark alert sent so we don't spam
                card.setExpiryAlertSent(true);
                cardRepository.save(card);

                log.info("Expiry alert sent for card: **** **** **** {}",
                        card.getCardLastFour());
            } catch (Exception e) {
                log.error("Failed to send expiry alert for card {}: {}",
                        card.getId(), e.getMessage());
            }
        });
    }

    // FIXED: replace findAll() with targeted query — avoids full table scan
    @Scheduled(cron = "0 0 0 * * ?")
    public void markExpiredCards() {
        List<Card> expiredCards = cardRepository
                .findActiveExpiredCards(LocalDate.now());

        expiredCards.forEach(card -> {
            card.setStatus(Card.CardStatus.EXPIRED);
            cardRepository.save(card);
            log.info("Card expired: **** **** **** {}", card.getCardLastFour());
        });

        if (!expiredCards.isEmpty()) {
            log.info("Marked {} cards as EXPIRED", expiredCards.size());
        }
    }
}