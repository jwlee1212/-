import json
from pathlib import Path
from PIL import Image

refs = Path("docs/refs/sprites")
assets = Path("godot/art/lobby")
street = json.loads(Path("config/content.json").read_text())["street"]["hs"]
buildings = Image.open(refs / "lobby_buildings_v1.png").convert("RGBA")
facades = Image.new("RGBA", (street["width"] // 2, street["height"] // 2))
for index, place in enumerate(street["places"]):
    col, row = index % 4, index // 4
    bounds = (col * buildings.width // 4, (0 if row == 0 else 468),
              (col + 1) * buildings.width // 4, (468 if row == 0 else buildings.height))
    sprite = buildings.crop(bounds)
    sprite = sprite.crop(sprite.getchannel("A").point(lambda a: 255 if a > 160 else 0).getbbox())
    sprite.thumbnail((round(place["w"] * 0.48), 365), Image.Resampling.LANCZOS)
    x = round((place["door"] - sprite.width) / 2)
    facades.alpha_composite(sprite, (x, street["groundY"] // 2 - sprite.height))
facades.save(assets / "hs_facades.png")

player = Image.open(refs / "lobby_player_v1.png").convert("RGBA")
top, bottom = player.getchannel("A").point(lambda a: 255 if a > 32 else 0).getbbox()[1::2]
cell_width = player.width // 5
walk = Image.new("RGBA", (cell_width * 5, bottom - top))
for frame in range(5):
    center = round((frame + 0.5) * player.width / 5)
    left = center - cell_width // 2
    walk.paste(player.crop((left, top, left + cell_width, bottom)), (frame * cell_width, 0))
walk.save(assets / "player.png")
