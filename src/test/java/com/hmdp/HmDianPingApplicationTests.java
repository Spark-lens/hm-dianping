package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    private IShopService shopService;

    @Autowired
    private CacheClient cacheClient;

    @Test
    void testSaveShop() throws InterruptedException {
        shopService.saveShop2Redis(1L,60L);
    }

    @Test
    void testSaveShopCacheClient() throws InterruptedException {
        Shop shop = shopService.getById(1);
        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY + shop.getId(),shop, CACHE_SHOP_TTL,TimeUnit.SECONDS);
    }


}
