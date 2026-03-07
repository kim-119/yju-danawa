import asyncio
from config import get_settings
from scraper_service import LibraryScraper
import json

async def main():
    settings = get_settings()
    scraper = LibraryScraper(settings)

    print("Testing '\uc774\ud399\ud2f0\ube0c \uc790\ubc14' by ISBN...")
    res = await scraper.check_library(isbn="9788966262281", title=None, author=None)
    print(json.dumps(res, indent=2, ensure_ascii=False))

    print("\nTesting '\ud074\ub9b0 \ucf54\ub4dc' by title...")
    res2 = await scraper.check_library(isbn=None, title="\ud074\ub9b0 \ucf54\ub4dc", author=None)
    print(json.dumps(res2, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    asyncio.run(main())
