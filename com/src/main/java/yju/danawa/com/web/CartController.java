package yju.danawa.com.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import yju.danawa.com.dto.CartItemDto;
import yju.danawa.com.service.CartItemService;
import yju.danawa.com.util.SecurityUtil;

import java.util.List;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartItemService cartItemService;
    private final SecurityUtil securityUtil;

    public CartController(CartItemService cartItemService, SecurityUtil securityUtil) {
        this.cartItemService = cartItemService;
        this.securityUtil = securityUtil;
    }

    @GetMapping
    public List<CartItemDto> getCart() {
        Long userId = securityUtil.getCurrentUserId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized"));
        return cartItemService.getCartItems(userId);
    }

    @PostMapping
    public List<CartItemDto> addToCart(@RequestBody CartAddRequest request) {
        Long userId = securityUtil.getCurrentUserId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized"));
        
        cartItemService.addToCart(userId, request.bookId(), request.quantity());
        
        // 추가 후 즉시 최신 리스트 반환 (실시간 갱신 느낌)
        return cartItemService.getCartItems(userId);
    }

    @DeleteMapping("/{bookId}")
    public List<CartItemDto> removeFromCart(@PathVariable String bookId) {
        Long userId = securityUtil.getCurrentUserId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized"));
        
        cartItemService.removeFromCart(userId, bookId);
        return cartItemService.getCartItems(userId);
    }

    public record CartAddRequest(String bookId, Integer quantity) {}
}
