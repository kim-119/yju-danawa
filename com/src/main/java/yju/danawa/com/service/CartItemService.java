package yju.danawa.com.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import yju.danawa.com.domain.CartItem;
import yju.danawa.com.dto.BookDto;
import yju.danawa.com.dto.CartItemDto;
import yju.danawa.com.repository.CartItemRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CartItemService {

    private final CartItemRepository cartItemRepository;
    private final BookService bookService;

    public CartItemService(CartItemRepository cartItemRepository, BookService bookService) {
        this.cartItemRepository = cartItemRepository;
        this.bookService = bookService;
    }

    @Transactional
    public void addToCart(Long userId, String bookId, Integer quantity) {
        cartItemRepository.findByUserIdAndBookId(userId, bookId)
                .ifPresentOrElse(
                        item -> item.setQuantity(item.getQuantity() + quantity),
                        () -> cartItemRepository.save(new CartItem(userId, bookId, quantity))
                );
    }

    public List<CartItemDto> getCartItems(Long userId) {
        return cartItemRepository.findByUserId(userId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private CartItemDto toDto(CartItem item) {
        BookDto book = bookService.findByIsbn13(item.getBookId()).orElse(null);
        return new CartItemDto(
                item.getId(),
                item.getBookId(),
                book != null ? book.title() : "알 수 없는 도서",
                book != null ? book.author() : "",
                book != null ? book.imageUrl() : null,
                item.getQuantity(),
                item.getCreatedAt()
        );
    }

    @Transactional
    public void removeFromCart(Long userId, String bookId) {
        cartItemRepository.deleteByUserIdAndBookId(userId, bookId);
    }
}
