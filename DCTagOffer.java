package com.dracave.tags.handlers;

import java.math.BigDecimal;
import java.util.Objects;

public record DCTagOffer(EcoType currency, BigDecimal price, String itemMaterial) {
    public DCTagOffer {
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(price, "price");
        price = price.stripTrailingZeros();
        if (price.signum() <= 0) {
            throw new IllegalArgumentException("shop.price must be greater than zero");
        }
        if (price.scale() > 4 || price.precision() - price.scale() > 15) {
            throw new IllegalArgumentException("shop.price must fit DECIMAL(19,4)");
        }
        if (currency == EcoType.PLAYER_POINTS || currency == EcoType.COIN || currency == EcoType.ITEM) {
            if (price.scale() > 0) {
                throw new IllegalArgumentException(currency.id() + " price must be a whole number");
            }
            if (price.compareTo(BigDecimal.valueOf(2147483647L)) > 0) {
                throw new IllegalArgumentException(currency.id() + " price exceeds 2147483647");
            }
        }
        if (currency == EcoType.ITEM && (itemMaterial == null || itemMaterial.isBlank())) {
            throw new IllegalArgumentException("item purchase requires itemMaterial");
        }
    }

    public DCTagOffer(EcoType currency, BigDecimal price) {
        this(currency, price, null);
    }

    public static DCTagOffer item(String material, int amount) {
        return new DCTagOffer(EcoType.ITEM, BigDecimal.valueOf(amount), material);
    }

    public String storedCurrency() {
        return currency == EcoType.ITEM ? "item:" + itemMaterial : currency.id();
    }

    public static DCTagOffer parseStored(String currencyName, BigDecimal price) {
        if (currencyName != null && currencyName.toLowerCase(java.util.Locale.ROOT).startsWith("item:")) {
            String material = currencyName.substring("item:".length());
            return new DCTagOffer(EcoType.ITEM, price, material);
        }
        return new DCTagOffer(EcoType.parse(currencyName), price);
    }
}
